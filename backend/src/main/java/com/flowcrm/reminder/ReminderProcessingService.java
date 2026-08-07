package com.flowcrm.reminder;

import com.flowcrm.enums.TaskStatus;
import com.flowcrm.messaging.ReminderMessage;
import com.flowcrm.outbox.FollowUpScheduledPayload;
import com.flowcrm.task.Task;
import com.flowcrm.task.TaskRepository;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes reminder messages idempotently using processed_messages (message_id = outbox event id).
 *
 * <p>Also skips stale messages (reschedule / complete / cancel races) without treating them as
 * delivery failures — no retry/DLQ for stale work.
 */
@Service
public class ReminderProcessingService {

    private static final Logger log = LoggerFactory.getLogger(ReminderProcessingService.class);

    private final ProcessedMessageRepository processedMessageRepository;
    private final ReminderDeliveryService reminderDeliveryService;
    private final TaskRepository taskRepository;

    public ReminderProcessingService(
            ProcessedMessageRepository processedMessageRepository,
            ReminderDeliveryService reminderDeliveryService,
            TaskRepository taskRepository) {
        this.processedMessageRepository = processedMessageRepository;
        this.reminderDeliveryService = reminderDeliveryService;
        this.taskRepository = taskRepository;
    }

    /**
     * @return true if delivery ran; false if duplicate or stale (both safely acknowledged)
     */
    @Transactional
    public boolean process(ReminderMessage message) {
        if (processedMessageRepository.existsById(message.eventId())) {
            log.info("Skipping duplicate reminder message eventId={}", message.eventId());
            return false;
        }

        if (isStale(message)) {
            acknowledgeWithoutDelivery(message.eventId(), "stale");
            return false;
        }

        reminderDeliveryService.deliver(message.payload());

        try {
            processedMessageRepository.saveAndFlush(new ProcessedMessage(message.eventId()));
        } catch (DataIntegrityViolationException ex) {
            // Concurrent duplicate delivery — treat as already processed.
            log.info("Duplicate reminder insert race for eventId={}", message.eventId());
            return false;
        }
        return true;
    }

    /**
     * Stale if the task is gone, not OPEN, has no reminder, or reminderAt no longer matches
     * the message (e.g. published just before a reschedule/complete race).
     */
    private boolean isStale(ReminderMessage message) {
        FollowUpScheduledPayload payload = message.payload();
        Task task = taskRepository.findById(payload.taskId()).orElse(null);
        if (task == null) {
            log.info(
                    "Skipping stale reminder eventId={} reason=task_missing taskId={}",
                    message.eventId(),
                    payload.taskId());
            return true;
        }
        if (task.getStatus() != TaskStatus.OPEN) {
            log.info(
                    "Skipping stale reminder eventId={} reason=task_not_open taskId={} status={}",
                    message.eventId(),
                    payload.taskId(),
                    task.getStatus());
            return true;
        }
        if (task.getReminderAt() == null) {
            log.info(
                    "Skipping stale reminder eventId={} reason=reminder_cleared taskId={}",
                    message.eventId(),
                    payload.taskId());
            return true;
        }
        if (!Objects.equals(task.getReminderAt(), payload.reminderAt())) {
            log.info(
                    "Skipping stale reminder eventId={} reason=reminder_mismatch taskId={} "
                            + "messageReminderAt={} taskReminderAt={}",
                    message.eventId(),
                    payload.taskId(),
                    payload.reminderAt(),
                    task.getReminderAt());
            return true;
        }
        return false;
    }

    private void acknowledgeWithoutDelivery(UUID eventId, String reason) {
        try {
            processedMessageRepository.saveAndFlush(new ProcessedMessage(eventId));
            log.info("Acknowledged {} reminder without delivery eventId={}", reason, eventId);
        } catch (DataIntegrityViolationException ex) {
            log.info("Duplicate {} acknowledgement race for eventId={}", reason, eventId);
        }
    }
}
