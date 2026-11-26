package com.campusoverflow.reputation.interfaces;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.campusoverflow.reputation.application.BountyService;
import com.campusoverflow.reputation.domain.BountySource;
import com.campusoverflow.shared.security.Actor;
import com.campusoverflow.shared.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** B06 悬赏端点：commandId / source / courseId 透传，缺省来源为个人账户。 */
class BountyControllerB06Test {

    private static final Actor STUDENT = new Actor(1, "小明", Role.STUDENT);

    private BountyService bounties;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        bounties = mock(BountyService.class);
        mvc = MockMvcBuilders.standaloneSetup(new BountyController(bounties))
                .setCustomArgumentResolvers(new FixedActorResolver(STUDENT))
                .build();
    }

    @Test
    void openPassesCommandIdSourceAndCourseId() throws Exception {
        when(bounties.open(any(), eq(10L), eq(50), eq(7), eq("cmd-1"),
                eq(BountySource.COURSE_BUDGET), eq(1L))).thenReturn(5L);

        mvc.perform(post("/api/v1/questions/10/bounty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\":50,\"days\":7,\"commandId\":\"cmd-1\","
                                + "\"source\":\"COURSE_BUDGET\",\"courseId\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(5));

        verify(bounties).open(any(), eq(10L), eq(50), eq(7), eq("cmd-1"),
                eq(BountySource.COURSE_BUDGET), eq(1L));
    }

    @Test
    void missingSourceDefaultsToPersonal() throws Exception {
        when(bounties.open(any(), eq(10L), eq(20), eq(3), isNull(),
                eq(BountySource.PERSONAL), isNull())).thenReturn(6L);

        mvc.perform(post("/api/v1/questions/10/bounty")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"points\":20,\"days\":3}"))
                .andExpect(status().isCreated());

        verify(bounties).open(any(), eq(10L), eq(20), eq(3), isNull(),
                eq(BountySource.PERSONAL), isNull());
    }
}
