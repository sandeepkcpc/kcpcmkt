package com.kcpc.mkt.web.mvc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kcpc.mkt.reporting.dto.UpcomingPlanDateGroup;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Serializes an Upcoming Channel Plan for embedding in a {@code <script type="application/json">}
 * block, mirroring {@code DeliverableMvcController#buildPlanningOptionsJson}'s established pattern
 * (embedded once with the page, never a second fetch).
 *
 * <p>Shared by the Idea Review &amp; Planning screens so the {@code "<"} escaping - which is what
 * stops a Channel handle containing {@code </script>} from breaking out of the block - has one
 * definition rather than one copy per controller.
 */
@Component
public class UpcomingChannelPlanJsonWriter {

    private final ObjectMapper objectMapper;

    public UpcomingChannelPlanJsonWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String write(List<UpcomingPlanDateGroup> groups) {
        try {
            return objectMapper.writeValueAsString(groups).replace("<", "\\u003c");
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize Upcoming Channel Plan calendar data", e);
        }
    }
}
