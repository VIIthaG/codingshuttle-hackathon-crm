package com.flowcrm.workqueue;

import com.flowcrm.call.Call;
import com.flowcrm.call.CallRepository;
import com.flowcrm.enums.CalendarItemType;
import com.flowcrm.enums.CallStatus;
import com.flowcrm.enums.MeetingStatus;
import com.flowcrm.enums.Role;
import com.flowcrm.enums.TaskStatus;
import com.flowcrm.meeting.Meeting;
import com.flowcrm.meeting.MeetingRepository;
import com.flowcrm.security.UserPrincipal;
import com.flowcrm.task.Task;
import com.flowcrm.task.TaskRepository;
import com.flowcrm.workqueue.dto.WorkqueueItemResponse;
import com.flowcrm.workqueue.dto.WorkqueueResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkqueueService {

    private static final Duration UPCOMING_WINDOW = Duration.ofDays(14);
    private static final int LIMIT = 50;

    private final TaskRepository taskRepository;
    private final MeetingRepository meetingRepository;
    private final CallRepository callRepository;

    public WorkqueueService(
            TaskRepository taskRepository, MeetingRepository meetingRepository, CallRepository callRepository) {
        this.taskRepository = taskRepository;
        this.meetingRepository = meetingRepository;
        this.callRepository = callRepository;
    }

    @Transactional(readOnly = true)
    public WorkqueueResponse get(UUID assignedToId, UserPrincipal principal) {
        UUID scope = principal.getRole() == Role.ADMIN ? assignedToId : principal.getId();
        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant startOfTomorrow = startOfToday.plus(Duration.ofDays(1));
        Instant upcomingEnd = now.plus(UPCOMING_WINDOW);

        List<Task> overdue = limit(scope == null
                ? taskRepository.findByStatusAndDueAtLessThanOrderByDueAtAsc(TaskStatus.OPEN, startOfToday)
                : taskRepository.findByAssignedTo_IdAndStatusAndDueAtLessThanOrderByDueAtAsc(
                        scope, TaskStatus.OPEN, startOfToday));
        List<Task> dueToday = limit(scope == null
                ? taskRepository.findByStatusAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
                        TaskStatus.OPEN, startOfToday, startOfTomorrow)
                : taskRepository.findByAssignedTo_IdAndStatusAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
                        scope, TaskStatus.OPEN, startOfToday, startOfTomorrow));
        List<Task> upcomingTasks = limit(scope == null
                ? taskRepository.findByStatusAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
                        TaskStatus.OPEN, startOfTomorrow, upcomingEnd)
                : taskRepository.findByAssignedTo_IdAndStatusAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
                        scope, TaskStatus.OPEN, startOfTomorrow, upcomingEnd));

        List<Meeting> todayMeetings = limit(scope == null
                ? meetingRepository.findByStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        MeetingStatus.SCHEDULED, startOfToday, startOfTomorrow)
                : meetingRepository.findByAssignedTo_IdAndStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        scope, MeetingStatus.SCHEDULED, startOfToday, startOfTomorrow));
        List<Meeting> upcomingMeetings = limit(scope == null
                ? meetingRepository.findByStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        MeetingStatus.SCHEDULED, startOfTomorrow, upcomingEnd)
                : meetingRepository.findByAssignedTo_IdAndStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        scope, MeetingStatus.SCHEDULED, startOfTomorrow, upcomingEnd));

        List<Call> todayCalls = limit(scope == null
                ? callRepository.findByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                        CallStatus.PLANNED, startOfToday, startOfTomorrow)
                : callRepository.findByAssignedTo_IdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                        scope, CallStatus.PLANNED, startOfToday, startOfTomorrow));
        List<Call> upcomingCalls = limit(scope == null
                ? callRepository.findByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                        CallStatus.PLANNED, startOfTomorrow, upcomingEnd)
                : callRepository.findByAssignedTo_IdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                        scope, CallStatus.PLANNED, startOfTomorrow, upcomingEnd));

        List<WorkqueueItemResponse> overdueItems =
                overdue.stream().map(t -> taskItem(t, "OVERDUE")).toList();
        List<WorkqueueItemResponse> dueTodayItems =
                dueToday.stream().map(t -> taskItem(t, "TODAY")).toList();
        List<WorkqueueItemResponse> upcomingTaskItems =
                upcomingTasks.stream().map(t -> taskItem(t, "UPCOMING")).toList();
        List<WorkqueueItemResponse> todayMeetingItems =
                todayMeetings.stream().map(m -> meetingItem(m, "TODAY")).toList();
        List<WorkqueueItemResponse> upcomingMeetingItems =
                upcomingMeetings.stream().map(m -> meetingItem(m, "UPCOMING")).toList();
        List<WorkqueueItemResponse> todayCallItems =
                todayCalls.stream().map(c -> callItem(c, "TODAY")).toList();
        List<WorkqueueItemResponse> upcomingCallItems =
                upcomingCalls.stream().map(c -> callItem(c, "UPCOMING")).toList();

        return new WorkqueueResponse(
                overdueItems,
                dueTodayItems,
                upcomingTaskItems,
                todayMeetingItems,
                upcomingMeetingItems,
                todayCallItems,
                upcomingCallItems,
                new WorkqueueResponse.WorkqueueCounts(
                        overdueItems.size(),
                        dueTodayItems.size(),
                        upcomingTaskItems.size(),
                        todayMeetingItems.size(),
                        upcomingMeetingItems.size(),
                        todayCallItems.size(),
                        upcomingCallItems.size()));
    }

    private <T> List<T> limit(List<T> source) {
        if (source.size() <= LIMIT) {
            return source;
        }
        return source.subList(0, LIMIT);
    }

    private WorkqueueItemResponse taskItem(Task task, String urgency) {
        return new WorkqueueItemResponse(
                task.getId(),
                CalendarItemType.TASK,
                task.getTitle(),
                task.getDueAt(),
                task.getStatus().name(),
                urgency,
                task.relatedType(),
                task.relatedId(),
                task.relatedName(),
                task.getAssignedTo().getFullName());
    }

    private WorkqueueItemResponse meetingItem(Meeting meeting, String urgency) {
        return new WorkqueueItemResponse(
                meeting.getId(),
                CalendarItemType.MEETING,
                meeting.getTitle(),
                meeting.getStartAt(),
                meeting.getStatus().name(),
                urgency,
                meeting.relatedType(),
                meeting.relatedId(),
                meeting.relatedName(),
                meeting.getAssignedTo().getFullName());
    }

    private WorkqueueItemResponse callItem(Call call, String urgency) {
        return new WorkqueueItemResponse(
                call.getId(),
                CalendarItemType.CALL,
                call.getTitle(),
                call.getScheduledAt(),
                call.getStatus().name(),
                urgency,
                call.relatedType(),
                call.relatedId(),
                call.relatedName(),
                call.getAssignedTo().getFullName());
    }
}
