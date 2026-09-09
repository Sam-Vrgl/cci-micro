package com.formation.notification.repository;

import com.formation.notification.model.Notification;
import com.formation.notification.model.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByUserIdOrderByCreatedDateDesc(Long userId);

    List<Notification> findByStatusInOrderByCreatedDateAsc(Collection<NotificationStatus> statuses);
}
