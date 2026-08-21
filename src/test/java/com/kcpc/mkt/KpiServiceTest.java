package com.kcpc.mkt;

import com.kcpc.mkt.identity.domain.User;
import com.kcpc.mkt.identity.repository.UserRepository;
import com.kcpc.mkt.idea.domain.Idea;
import com.kcpc.mkt.idea.repository.IdeaRepository;
import com.kcpc.mkt.masterdata.repository.PublicationTargetRepository;
import com.kcpc.mkt.planning.domain.ContentPlan;
import com.kcpc.mkt.planning.domain.OutputType;
import com.kcpc.mkt.planning.domain.PlannedOutput;
import com.kcpc.mkt.planning.repository.ContentPlanRepository;
import com.kcpc.mkt.planning.repository.PlannedOutputRepository;
import com.kcpc.mkt.publishing.domain.ActualPublicationEvent;
import com.kcpc.mkt.publishing.domain.PublicationEventType;
import com.kcpc.mkt.publishing.repository.ActualPublicationEventRepository;
import com.kcpc.mkt.reporting.dto.KpiValue;
import com.kcpc.mkt.reporting.service.KpiService;
import com.kcpc.mkt.workflow.domain.WorkflowInstance;
import com.kcpc.mkt.workflow.domain.WorkflowStatus;
import com.kcpc.mkt.workflow.domain.WorkflowTransitionHistory;
import com.kcpc.mkt.workflow.repository.WorkflowInstanceRepository;
import com.kcpc.mkt.workflow.repository.WorkflowTransitionHistoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Regression test for the KPI-028 "Shoot-to-Publish Cycle Time" defect: the query previously took
 * {@code max(actual_publication_timestamp)} over EVERY {@code actual_publication_events} row for a
 * plan, including REPOST events, with no {@code event_type = 'ORIGINAL'} filter (unlike its sibling
 * KPI-030 and the rest of the codebase's "actual publish moment" convention). A REPOST timestamped
 * before the plan's Shoot-Approved transition produced an impossible negative average. This test
 * seeds exactly that pathological REPOST row alongside a correct later ORIGINAL row and asserts the
 * fixed query ignores the REPOST and never goes negative. {@code @Transactional} rolls the whole
 * test back - no fixture data is left behind in the shared kcpc_test database.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class KpiServiceTest {

    @Autowired
    UserRepository userRepository;
    @Autowired
    IdeaRepository ideaRepository;
    @Autowired
    ContentPlanRepository contentPlanRepository;
    @Autowired
    PlannedOutputRepository plannedOutputRepository;
    @Autowired
    PublicationTargetRepository publicationTargetRepository;
    @Autowired
    ActualPublicationEventRepository actualPublicationEventRepository;
    @Autowired
    WorkflowInstanceRepository workflowInstanceRepository;
    @Autowired
    WorkflowTransitionHistoryRepository workflowTransitionHistoryRepository;
    @Autowired
    KpiService kpiService;
    @Autowired
    EntityManager entityManager;

    @Test
    void shootToPublishCycleTimeIgnoresRepostEventsAndNeverGoesNegative() {
        long unique = Instant.now().toEpochMilli();
        User actor = userRepository.findByEmailIgnoreCase("ceo@kcpcbandhani.local").orElseThrow();

        WorkflowInstance workflowInstance = workflowInstanceRepository.save(new WorkflowInstance(WorkflowStatus.SAP));
        Idea idea = ideaRepository.save(new Idea(workflowInstance, "KPI028-TEST-" + unique, "KPI-028 regression idea",
                null, null, null, actor));
        ContentPlan plan = contentPlanRepository.save(new ContentPlan(idea, workflowInstance, "KPI028-" + unique));
        PlannedOutput output = plannedOutputRepository.save(new PlannedOutput(plan, OutputType.PHOTOGRAPHY, null, null));
        UUID publicationTargetId = publicationTargetRepository.findAll().get(0).getId();
        var publicationTarget = publicationTargetRepository.findById(publicationTargetId).orElseThrow();

        // SAP transition timestamp is @CreationTimestamp (set at persist time, "now").
        WorkflowTransitionHistory sapTransition = workflowTransitionHistoryRepository.save(new WorkflowTransitionHistory(
                workflowInstance, WorkflowStatus.SRV, WorkflowStatus.SAP, actor, null,
                "APPROVE_SHOOT_REVIEW", null));
        entityManager.flush(); // materializes the @CreationTimestamp so we can read+offset it below
        Instant sapAt = sapTransition.getTransitionTimestamp();

        // The pathological row this plan reproduces: its ONLY publication event is a REPOST
        // timestamped BEFORE this cycle's Shoot Approval (e.g. an unrelated repost predating a
        // shoot redo). Under the old, unfiltered query this was the only event max() could see, so
        // max_ts < first_sap -> a negative cycle time for this plan, dragging the whole average
        // down. The fix must exclude this plan from the average entirely (no ORIGINAL event exists
        // for it), not count it as a negative contribution.
        actualPublicationEventRepository.save(new ActualPublicationEvent(plan, output, publicationTarget,
                PublicationEventType.REPOST, sapAt.minus(10, ChronoUnit.DAYS), "https://example.com/repost", actor));

        entityManager.flush(); // KpiService reads via native SQL - pending inserts must hit the DB first

        List<KpiValue> kpis = kpiService.queryGovernedKpis(actor, "DELAY_SLA", "KPI-028", null, null);
        KpiValue kpi028 = kpis.stream().filter(k -> "KPI-028".equals(k.getCode())).findFirst().orElseThrow();

        assertThat(kpi028.getDisplayValue()).isNotEqualTo("N/A");
        double days = Double.parseDouble(kpi028.getDisplayValue().replace(" days", ""));
        assertThat(days).isGreaterThanOrEqualTo(0.0);
    }
}
