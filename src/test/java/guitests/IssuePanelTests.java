package guitests;

import static org.junit.Assert.assertEquals;
import github.IssueEventType;
import github.TurboIssueEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.GregorianCalendar;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.User;
import org.junit.Test;

import tests.TurboIssueEventTests;
import ui.UI;
import ui.listpanel.ListPanel;
import ui.listpanel.ListPanelCard;
import util.events.testevents.UILogicRefreshEvent;
import util.events.testevents.UpdateDummyRepoEvent;
import backend.resource.Model;
import backend.resource.TurboIssue;
import backend.resource.TurboLabel;

public class IssuePanelTests extends UITest {

    private static final int EVENT_DELAY = 1000;

    @Test
    public void keepSelectionTest() {
        // checks to see if ListPanel keeps the same issue selected even after
        // the list is updated
        ListPanel issuePanel = find("#dummy/dummy_col0");
        click("#dummy/dummy_col0_filterTextField");
        type("sort");
        press(KeyCode.SHIFT).press(KeyCode.SEMICOLON).release(KeyCode.SEMICOLON).release(KeyCode.SHIFT);
        type("date");
        push(KeyCode.ENTER);
        push(KeyCode.SPACE).push(KeyCode.SPACE);
        push(KeyCode.DOWN).push(KeyCode.DOWN);
        sleep(EVENT_DELAY);
        assertEquals(3, issuePanel.getSelectedIssue().getId());
        sleep(EVENT_DELAY);
        UI.events.triggerEvent(UpdateDummyRepoEvent.updateIssue("dummy/dummy", 3, "updated issue"));
        UI.events.triggerEvent(new UILogicRefreshEvent());
        sleep(EVENT_DELAY);
        assertEquals(3, issuePanel.getSelectedIssue().getId());
    }

    @Test
    public void testCreateLabelUpdateEventNodesForNoEvent() {
        Model sampleModel = new Model("test/test", null, null, null, null);

        assertEquals(0,
                TurboIssueEvent.createLabelUpdateEventNodes(
                        sampleModel, new ArrayList<TurboIssueEvent>()).size());
    }

    @Test
    public void testCreateLabelUpdateEventNodesForSampleEvents()
            throws IllegalAccessException, IllegalArgumentException, InvocationTargetException,
                   NoSuchMethodException, SecurityException {

        Method layoutMethod = ListPanelCard.class.getDeclaredMethod(
                "layoutEvents", Model.class, TurboIssue.class, List.class, List.class);
        layoutMethod.setAccessible(true);

        List<TurboLabel> labels = new ArrayList<>();
        labels.add(new TurboLabel("test/test", "A1"));
        labels.add(new TurboLabel("test/test", "A2"));
        labels.add(new TurboLabel("test/test", "B1"));
        labels.add(new TurboLabel("test/test", "B2"));
        labels.add(new TurboLabel("test/test", "C1"));
        labels.add(new TurboLabel("test/test", "D1"));
        Model sampleModel = new Model("test/test", null, labels, null, null);

        List<TurboIssueEvent> events =
                new ArrayList<>(new TurboIssueEventTests().sampleEvents);
        List<Node> nodes = TurboIssueEvent.createLabelUpdateEventNodes(sampleModel, events);

        assertEquals(5, TurboIssueEvent.createLabelUpdateEventNodes(sampleModel, events).size());
        assertEquals(5, ((HBox) nodes.get(0)).getChildren().size());
        assertEquals(5, ((HBox) nodes.get(1)).getChildren().size());
        assertEquals(4, ((HBox) nodes.get(2)).getChildren().size());
        assertEquals(4, ((HBox) nodes.get(3)).getChildren().size());
        assertEquals(4, ((HBox) nodes.get(4)).getChildren().size());
        assertEquals(5, ((VBox) layoutMethod.invoke(null,
                                    sampleModel, new TurboIssue("test/test", 1, "issue"),
                                    events, new ArrayList<Comment>())).getChildren().size());
    }

    @Test
    public void testCreateLabelUpdateEventNodesForNonExistentLabel() {
        List<TurboLabel> labels = new ArrayList<>();
        Model sampleModel = new Model("test/test", null, labels, null, null);

        List<TurboIssueEvent> events = new ArrayList<>();
        events.add(
            new TurboIssueEvent(
                new User().setLogin("A"), IssueEventType.Labeled,
                new GregorianCalendar(2015, 1, 1, 1, 1, 0).getTime())
            .setLabelName("X"));

        assertEquals(1,
                TurboIssueEvent.createLabelUpdateEventNodes(
                        sampleModel, events).size());
    }
}
