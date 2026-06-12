package nz.co.jammehcow.jenkinsdiscord;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BasicTest {

    @Test
    void webhookClassDoesntThrow() {
        assertDoesNotThrow(() -> {
            DiscordWebhook wh = new DiscordWebhook("http://exampl.e");
            wh.setContent("content");
            wh.setDescription("desc");
            wh.setStatus(DiscordWebhook.StatusColor.GREEN);
            wh.send();
        });
    }

    @Test
    void pipelineDoesntThrow() {
        assertDoesNotThrow(() -> {
            DiscordPipelineStep step = new DiscordPipelineStep("http://exampl.e");
            step.setTitle("Test title");
            DiscordPipelineStep.DiscordPipelineStepExecution execution =
                    new DiscordPipelineStep.DiscordPipelineStepExecution();
            execution.step = step;
            execution.listener = () -> System.out;
            execution.run();
        });
    }

    @Test
    void parseColorHexWithHash() {
        assertEquals(0x19A959, DiscordWebhook.parseColor("#19A959"));
    }

    @Test
    void parseColorHexWithoutHash() {
        assertEquals(0x19A959, DiscordWebhook.parseColor("19A959"));
    }

    @Test
    void parseColorDecimal() {
        assertEquals(1681177, DiscordWebhook.parseColor("1681177"));
    }

    @Test
    void parseColorInvalidThrows() {
        assertThrows(NumberFormatException.class, () -> DiscordWebhook.parseColor("not-a-color"));
    }

    @Test
    void setStatusByColorDoesntThrow() {
        assertDoesNotThrow(() -> {
            DiscordWebhook wh = new DiscordWebhook("http://exampl.e");
            wh.setStatusByColor(0xFF0000);
        });
    }

    @Test
    void pipelineWithCustomColorsDoesntThrow() {
        assertDoesNotThrow(() -> {
            DiscordPipelineStep step = new DiscordPipelineStep("http://exampl.e");
            step.setTitle("Test title");
            step.setSuccessColor("#00FF00");
            step.setUnstableColor("#FFFF00");
            step.setFailureColor("#FF0000");
            step.setAbortedColor("#AAAAAA");
            DiscordPipelineStep.DiscordPipelineStepExecution execution =
                    new DiscordPipelineStep.DiscordPipelineStepExecution();
            execution.step = step;
            execution.listener = () -> System.out;
            execution.run();
        });
    }
}
