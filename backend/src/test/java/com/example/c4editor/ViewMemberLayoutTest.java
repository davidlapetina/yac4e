package com.example.c4editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.c4editor.application.AgentProposalService;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/**
 * Covers the deterministic geometry an agent gets when it proposes view members without
 * coordinates. Agents should not have to compute diagram layout to create a usable view.
 */
class ViewMemberLayoutTest {
    private static double autoX(int position, double width) throws Exception {
        Method method = AgentProposalService.class.getDeclaredMethod("autoX", int.class, double.class);
        method.setAccessible(true);
        return (double) method.invoke(null, position, width);
    }

    private static double autoY(int position, double height) throws Exception {
        Method method = AgentProposalService.class.getDeclaredMethod("autoY", int.class, double.class);
        method.setAccessible(true);
        return (double) method.invoke(null, position, height);
    }

    private static double positive(Double value, double fallback) throws Exception {
        Method method = AgentProposalService.class.getDeclaredMethod("positive", Double.class, double.class);
        method.setAccessible(true);
        return (double) method.invoke(null, value, fallback);
    }

    @Test
    void firstRowAdvancesByWidthPlusGap() throws Exception {
        assertEquals(0, autoX(0, 260));
        assertEquals(320, autoX(1, 260));
        assertEquals(640, autoX(2, 260));
        assertEquals(960, autoX(3, 260));
        for (int position = 0; position < 4; position++) {
            assertEquals(0, autoY(position, 150), "first four members stay on the first row");
        }
    }

    @Test
    void wrapsToANewRowAfterFourColumns() throws Exception {
        assertEquals(0, autoX(4, 260));
        assertEquals(210, autoY(4, 150));
        assertEquals(320, autoX(5, 260));
        assertEquals(210, autoY(5, 150));
        assertEquals(420, autoY(8, 150), "ninth member starts the third row");
    }

    @Test
    void layoutTracksTheMemberSizeRatherThanAssumingDefaults() throws Exception {
        assertEquals(160, autoX(1, 100));
        assertEquals(160, autoY(4, 100));
    }

    @Test
    void nonPositiveDimensionsFallBackToDefaults() throws Exception {
        assertEquals(260, positive(null, 260));
        assertEquals(260, positive(0.0, 260));
        assertEquals(260, positive(-5.0, 260));
        assertEquals(400, positive(400.0, 260));
    }
}
