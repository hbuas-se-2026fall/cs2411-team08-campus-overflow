package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.discovery.application.fake.DiscoveryFixtures;
import com.campusoverflow.discovery.application.fake.DiscoveryFixtures.InMemoryNotifications;
import com.campusoverflow.discovery.application.fake.DiscoveryFixtures.StubIdentity;
import com.campusoverflow.discovery.application.fake.DiscoveryFixtures.StubQa;
import com.campusoverflow.discovery.domain.notification.Notification;
import com.campusoverflow.discovery.domain.notification.NotificationType;
import com.campusoverflow.qa.api.event.AnswerAcceptedEvent;
import com.campusoverflow.qa.api.event.CommentPostedEvent;
import com.campusoverflow.reputation.api.event.BadgeAwardedEvent;
import com.campusoverflow.shared.security.Role;
import org.junit.jupiter.api.Test;

class NotificationProjectorTest {

    private final InMemoryNotifications notifications = new InMemoryNotifications();
    private final StubIdentity identity = new StubIdentity();
    private final StubQa qa = new StubQa();
    private final NotificationProjector projector = new NotificationProjector(notifications, identity, qa);

    @Test
    void acceptedNotifiesAnswerAuthorOnce() {
        identity.user(2, "小红", Role.STUDENT);
        AnswerAcceptedEvent event = new AnswerAcceptedEvent("e1", DiscoveryFixtures.NOW, 50, 10, 2, 1, 1, "标题");

        projector.on(event);
        projector.on(event);

        assertThat(notifications.items).hasSize(1);
        assertThat(notifications.items.get(0).type()).isEqualTo(NotificationType.ANSWER_ACCEPTED);
        assertThat(notifications.items.get(0).recipientId()).isEqualTo(2);
    }

    @Test
    void mentionNotifiesOthersButNotSelf() {
        identity.user(2, "小红", Role.STUDENT).user(1, "小明", Role.STUDENT);

        projector.on(new CommentPostedEvent("c1", DiscoveryFixtures.NOW, 1, 10, 2, "感谢 @小明，@小红 也看看"));

        assertThat(notifications.items).hasSize(1);
        assertThat(notifications.items.get(0).recipientId()).isEqualTo(1);
        assertThat(notifications.items.get(0).type()).isEqualTo(NotificationType.MENTIONED);
    }

    @Test
    void badgeAwardedNotifiesUser() {
        projector.on(new BadgeAwardedEvent("b1", DiscoveryFixtures.NOW, 2, "FIRST_ACCEPTED", "初露锋芒"));

        assertThat(notifications.items).extracting(Notification::recipientId).containsExactly(2L);
    }
}
