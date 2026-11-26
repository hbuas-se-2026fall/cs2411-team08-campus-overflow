package com.campusoverflow.discovery.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.campusoverflow.discovery.application.fake.DiscoveryFixtures;
import com.campusoverflow.discovery.application.fake.DiscoveryFixtures.InMemorySearchIndex;
import com.campusoverflow.discovery.application.fake.DiscoveryFixtures.StubIdentity;
import com.campusoverflow.discovery.application.fake.DiscoveryFixtures.StubSearchPort;
import com.campusoverflow.discovery.domain.search.SearchHit;
import com.campusoverflow.discovery.domain.search.SearchIndex;
import com.campusoverflow.discovery.domain.search.SearchQuery;
import com.campusoverflow.shared.page.PageRequest;
import com.campusoverflow.shared.security.Role;
import java.util.List;
import org.junit.jupiter.api.Test;

class DiscoveryQueryServiceTest {

    private final StubSearchPort port = new StubSearchPort();
    private final InMemorySearchIndex index = new InMemorySearchIndex();
    private final StubIdentity identity = new StubIdentity();
    private final DiscoveryQueryService service = new DiscoveryQueryService(port, index, identity);

    @Test
    void searchFillsAuthorProfile() {
        identity.user(2, "小红", Role.STUDENT);
        port.hit(new SearchHit(10, "标题", "摘要", List.of("数据结构"), 1L, 2, 5, 3, true, false,
                DiscoveryFixtures.NOW));

        SearchResultView view = service
                .search(new SearchQuery("数据结构", null, null, null, false, false, new PageRequest(1, 20)))
                .items().get(0);

        assertThat(view.author().displayName()).isEqualTo("小红");
        assertThat(view.accepted()).isTrue();
        assertThat(view.tags()).containsExactly("数据结构");
    }

    @Test
    void relatedRanksByJaccard() {
        InMemorySearchIndex.Doc own = new InMemorySearchIndex.Doc();
        own.tags = List.of("数据结构", "跳表", "红黑树");
        index.docs.put(10L, own);
        index.candidates = List.of(
                new SearchIndex.RelatedCandidate(1, "A", 1, 5, 99, 3, true),
                new SearchIndex.RelatedCandidate(2, "B", 2, 2, 0, 0, false),
                new SearchIndex.RelatedCandidate(3, "C", 2, 3, 5, 1, true));

        List<RelatedQuestionView> related = service.related(10, 2);

        assertThat(related).extracting(RelatedQuestionView::id).containsExactly(2L, 3L);
    }

    @Test
    void relatedReturnsEmptyWhenNoTags() {
        assertThat(service.related(99, 5)).isEmpty();
    }
}
