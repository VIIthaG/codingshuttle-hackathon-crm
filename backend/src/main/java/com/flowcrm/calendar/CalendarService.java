package com.flowcrm.calendar;

import com.flowcrm.call.Call;
import com.flowcrm.call.CallRepository;
import com.flowcrm.calendar.dto.CalendarItemResponse;
import com.flowcrm.calendar.dto.CalendarResponse;
import com.flowcrm.common.exception.BadRequestException;
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
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CalendarService {

    private final TaskRepository taskRepository;
    private final MeetingRepository meetingRepository;
    private final CallRepository callRepository;

    public CalendarService(
            TaskRepository taskRepository, MeetingRepository meetingRepository, CallRepository callRepository) {
        this.taskRepository = taskRepository;
        this.meetingRepository = meetingRepository;
        this.callRepository = callRepository;
    }

    @Transactional(readOnly = true)
    public CalendarResponse list(Instant from, Instant to, UUID assignedToId, UserPrincipal principal) {
        Instant[] window = resolveWindow(from, to);
        Instant start = window[0];
        Instant end = window[1];
        UUID scopeAssignee = resolveAssigneeFilter(assignedToId, principal);

        List<CalendarItemResponse> items = new ArrayList<>();
        List<Task> tasks = scopeAssignee == null
                ? taskRepository.findByStatusAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
                        TaskStatus.OPEN, start, end)
                : taskRepository.findByAssignedTo_IdAndStatusAndDueAtGreaterThanEqualAndDueAtLessThanOrderByDueAtAsc(
                        scopeAssignee, TaskStatus.OPEN, start, end);
        for (Task task : tasks) {
            items.add(new CalendarItemResponse(
                    task.getId(),
                    CalendarItemType.TASK,
                    task.getTitle(),
                    task.getDueAt(),
                    null,
                    task.getStatus().name(),
                    task.relatedType(),
                    task.relatedId(),
                    task.relatedName(),
                    task.getAssignedTo().getId(),
                    task.getAssignedTo().getFullName(),
                    Map.of("dueAt", task.getDueAt().toString())));
        }

        List<Meeting> meetings = scopeAssignee == null
                ? meetingRepository.findByStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        MeetingStatus.SCHEDULED, start, end)
                : meetingRepository.findByAssignedTo_IdAndStatusAndStartAtGreaterThanEqualAndStartAtLessThanOrderByStartAtAsc(
                        scopeAssignee, MeetingStatus.SCHEDULED, start, end);
        for (Meeting meeting : meetings) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("startAt", meeting.getStartAt().toString());
            meta.put("endAt", meeting.getEndAt().toString());
            items.add(new CalendarItemResponse(
                    meeting.getId(),
                    CalendarItemType.MEETING,
                    meeting.getTitle(),
                    meeting.getStartAt(),
                    meeting.getEndAt(),
                    meeting.getStatus().name(),
                    meeting.relatedType(),
                    meeting.relatedId(),
                    meeting.relatedName(),
                    meeting.getAssignedTo().getId(),
                    meeting.getAssignedTo().getFullName(),
                    meta));
        }

        List<Call> calls = scopeAssignee == null
                ? callRepository.findByStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                        CallStatus.PLANNED, start, end)
                : callRepository.findByAssignedTo_IdAndStatusAndScheduledAtGreaterThanEqualAndScheduledAtLessThanOrderByScheduledAtAsc(
                        scopeAssignee, CallStatus.PLANNED, start, end);
        for (Call call : calls) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("direction", call.getDirection().name());
            meta.put("scheduledAt", call.getScheduledAt().toString());
            items.add(new CalendarItemResponse(
                    call.getId(),
                    CalendarItemType.CALL,
                    call.getTitle(),
                    call.getScheduledAt(),
                    null,
                    call.getStatus().name(),
                    call.relatedType(),
                    call.relatedId(),
                    call.relatedName(),
                    call.getAssignedTo().getId(),
                    call.getAssignedTo().getFullName(),
                    meta));
        }

        items.sort(Comparator.comparing(CalendarItemResponse::startAt)
                .thenComparing(item -> item.itemType().name())
                .thenComparing(CalendarItemResponse::id));
        return new CalendarResponse(start, end, items);
    }

    private UUID resolveAssigneeFilter(UUID assignedToId, UserPrincipal principal) {
        if (principal.getRole() != Role.ADMIN) {
            return principal.getId();
        }
        return assignedToId;
    }

    private Instant[] resolveWindow(Instant from, Instant to) {
        if (from == null && to == null) {
            YearMonth month = YearMonth.now(ZoneOffset.UTC);
            Instant start = month.atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant end = month.plusMonths(1).atDay(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            return new Instant[] {start, end};
        }
        if (from == null || to == null) {
            throw new BadRequestException("Both from and to are required when specifying a calendar window");
        }
        if (!to.isAfter(from)) {
            throw new BadRequestException("to must be after from");
        }
        return new Instant[] {from, to};
    }
}
