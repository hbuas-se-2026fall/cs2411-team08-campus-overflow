package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.discovery.application.fake.DiscoveryFixtures;
import com.campusoverflow.discovery.application.fake.DiscoveryFixtures.InMemoryProcessed;
import com.campusoverflow.discovery.application.fake.DiscoveryFixtures.InMemorySearchIndex;
import com.campusoverflow.qa.api.event.AnswerSubmittedEvent;
import com.campusoverflow.qa.api.event.ContentDeletedEvent;
import com.campusoverflow.qa.api.event.QuestionPostedEvent;
import com.campusoverflow.qa.api.event.VoteCastEvent;
import com.campusoverflow.reputation.api.event.BountyClosedEvent;
import com.campusoverflow.reputation.api.event.BountyOpenedEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchIndexProjectorTest {

    private final InMemorySearchIndex index = new InMemorySearchIndex();
    private final InMemoryProcessed processed = new InMemoryProcessed();
    private final SearchIndexProjector projector = new SearchIndexProjector(index, processed);

    private QuestionPostedEvent posted() {
        return new QuestionPostedEvent("q1", DiscoveryFixtures.NOW, 10, 2, 1L, "标题", "正文", List.of("数据结构"));
    }

    @Test
    void questionPostedIndexesOnlyOnce() {
        projector.on(posted());
        projector.on(posted());

        assertThat(index.actions).containsExactly("insert:10");
    }

    @Test
    void answerSubmittedIncrementsCountOnce() {
        projector.on(posted());
        AnswerSubmittedEvent event = new AnswerSubmittedEvent("a1", DiscoveryFixtures.NOW, 50, 10, 3, 2, "标题");

        projector.on(event);
        projector.on(event);

        assertThat(index.docs.get(10L).answerCount).isEqualTo(1);
    }

    @Test
    void votingOnQuestionAdjustsScore() {
        projector.on(posted());
        projector.on(new VoteCastEvent("v1", DiscoveryFixtures.NOW, "QUESTION", 10, 2, 3, 10, 0, 1));

        assertThat(index.docs.get(10L).score).isEqualTo(1);
    }

    @Test
    void questionDeletionMarksDocDeleted() {
        projector.on(posted());
        projector.on(new ContentDeletedEvent("d1", DiscoveryFixtures.NOW, "QUESTION", 10, 10));

        assertThat(index.docs.get(10L).deleted).isTrue();
    }

    @Test
    void bountyLifecycleTogglesFlag() {
        projector.on(posted());
        projector.on(new BountyOpenedEvent("bo", DiscoveryFixtures.NOW, 1, 10, 9, 50,
                DiscoveryFixtures.NOW.plusSeconds(3600)));
        assertThat(index.docs.get(10L).bountyOpen).isTrue();

        projector.on(new BountyClosedEvent("bc", DiscoveryFixtures.NOW, 1, 10, 9, 50));
        assertThat(index.docs.get(10L).bountyOpen).isFalse();
    }
}
