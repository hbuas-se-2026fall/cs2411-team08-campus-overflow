package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.campusoverflow.discovery.application.fake.DiscoveryFixtures;
import com.campusoverflow.discovery.application.fake.DiscoveryFixtures.InMemoryNotifications;
import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.shared.domain.ForbiddenException;
import com.campusoverflow.shared.domain.NotFoundException;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import org.junit.jupiter.api.Test;

class NotificationServiceTest {

    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final NotificationService service = new NotificationService(notifications);

    private final Actor owner = new Actor(2, "小红", Role.STUDENT);
    private final Actor other = new Actor(3, "小明", Role.STUDENT);

    @Test
    void listMapsNotifications() {
        notifications.save(Notification.create(2, NotificationType.ANSWER_ACCEPTED, "标题", "/q/10", "e1",
                DiscoveryFixtures.NOW));

        assertThat(service.list(owner, new PageRequest(1, 20)).items())
                .extracting(NotificationView::type).containsExactly("ANSWER_ACCEPTED");
    }

    @Test
    void unreadCountAndMarkAllRead() {
        notifications.save(Notification.create(2, NotificationType.ANSWER_ACCEPTED, "t", "/q/10", "e1",
                DiscoveryFixtures.NOW));
        notifications.save(Notification.create(2, NotificationType.ANSWER_RECEIVED, "t", "/q/10", "e2",
                DiscoveryFixtures.NOW));

        assertThat(service.unreadCount(owner)).isEqualTo(2);
        assertThat(service.markAllRead(owner)).isEqualTo(2);
        assertThat(service.unreadCount(owner)).isZero();
    }

    @Test
    void markReadRejectsOtherRecipient() {
        Notification notification = Notification.create(2, NotificationType.ANSWER_ACCEPTED, "t", "/q/10", "e1",
                DiscoveryFixtures.NOW);
        notifications.save(notification);

        assertThatThrownBy(() -> service.markRead(other, notification.id()))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void markReadUnknownThrowsNotFound() {
        assertThatThrownBy(() -> service.markRead(owner, 999)).isInstanceOf(NotFoundException.class);
    }
}
