package ui.components.pickers;

import backend.resource.TurboIssue;
import backend.resource.TurboLabel;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LabelPickerDialog extends Dialog<List<String>> {

    public static final int VBOX_SPACING = 105; // seems like some magic number

    private TextField textField;
    private List<TurboLabel> allLabels;
    private List<PickerLabel> topLabels;
    private List<PickerLabel> bottomLabels;
    private Map<String, Boolean> resultList;
    private FlowPane topPane;
    private FlowPane bottomPane;
    private VBox vBox;

    LabelPickerDialog(TurboIssue issue, List<TurboLabel> allLabels, Stage stage) {
        initOwner(stage);
        initModality(Modality.APPLICATION_MODAL); // TODO change to NONE for multiple dialogs
        setTitle("Edit Labels for " + (issue.isPullRequest() ? "PR #" : "Issue #") +
                issue.getId() + " in " + issue.getRepoId());
        setHeaderText((issue.isPullRequest() ? "PR #" : "Issue #") + issue.getId() + ": " + issue.getTitle());

        this.allLabels = allLabels;
        resultList = new HashMap<>();
        topLabels = new ArrayList<>();
        allLabels.forEach(label ->
                resultList.put(label.getActualName(), issue.getLabels().contains(label.getActualName())));

        ButtonType confirmButtonType = new ButtonType("Confirm", ButtonBar.ButtonData.OK_DONE);
        getDialogPane().getButtonTypes().addAll(confirmButtonType, ButtonType.CANCEL);

        vBox = new VBox();
        vBox.setPadding(new Insets(10));
        vBox.setPrefHeight(1);

        topPane = new FlowPane();
        topPane.setPadding(new Insets(0, 0, 10, 0));
        topPane.setHgap(5);
        topPane.setVgap(5);

        textField = new TextField();
        textField.setPrefColumnCount(30);
        setupKeyEvents();

        bottomPane = new FlowPane();
        bottomPane.setPadding(new Insets(10, 0, 0, 0));
        bottomPane.setHgap(5);
        bottomPane.setVgap(5);

        updateTopLabels();
        populateTopPanel();
        updateBottomLabels("");
        populateBottomPane();

        vBox.getChildren().addAll(topPane, textField, bottomPane);
        getDialogPane().setContent(vBox);
        vBox.heightProperty().addListener((observable, oldValue, newValue) -> {
            setHeight(newValue.intValue() + VBOX_SPACING);
        });

        setResultConverter(dialogButton -> {
            if (dialogButton == confirmButtonType) {
                return resultList.entrySet().stream()
                        .filter(Map.Entry::getValue)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
            }
            return null;
        });
        requestFocus();
    }

    protected void requestFocus() {
        Platform.runLater(textField::requestFocus);
    }

    private void populateTopPanel() {
        topPane.getChildren().clear();
        topLabels.forEach(label -> topPane.getChildren().add(label.getNode()));
        if (topPane.getChildren().size() == 0) {
            Label label = new Label("No currently selected labels. ");
            label.setPadding(new Insets(2, 5, 2, 5));
            topPane.getChildren().add(label);
        }
    }

    private void populateBottomPane() {
        bottomPane.getChildren().clear();
        bottomLabels.forEach(label -> bottomPane.getChildren().add(label.getNode()));
        if (bottomPane.getChildren().size() == 0) {
            Label label = new Label("No labels in repository. ");
            label.setPadding(new Insets(2, 5, 2, 5));
            bottomPane.getChildren().add(label);
        }
    }

    private void setupKeyEvents() {
        textField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue.equals(" ")) {
                textField.setText("");
            } else {
                updateBottomLabels(newValue.toLowerCase());
                populateBottomPane();
            }
        });
        textField.setOnKeyPressed(e -> {
            if (!e.isAltDown() && !e.isMetaDown() && !e.isControlDown()) {
                if (e.getCode() == KeyCode.DOWN) {
                    e.consume();
                    moveHighlightOnLabel(true);
                    populateBottomPane();
                } else if (e.getCode() == KeyCode.UP) {
                    e.consume();
                    moveHighlightOnLabel(false);
                    populateBottomPane();
                } else if (e.getCode() == KeyCode.SPACE) {
                    e.consume();
                    toggleSelectedLabel();
                    updateTopLabels();
                    updateBottomLabels("");
                    textField.setText("");
                    populateTopPanel();
                    populateBottomPane();
                }
            }
        });
    }

    public void toggleLabel(String name) {
        processLabelChange(name);
        updateTopLabels();
        populateTopPanel();
        textField.setText("");
        updateBottomLabels("");
        populateBottomPane();
    }

    private void toggleSelectedLabel() {
        if (!bottomLabels.isEmpty()) {
            processLabelChange(
                    bottomLabels.stream().filter(PickerLabel::isHighlighted).findFirst().get().getActualName());
        }
    }

    private void processLabelChange(String name) {
        Optional<TurboLabel> turboLabel =
                allLabels.stream().filter(label -> label.getActualName().equals(name)).findFirst();
        if (turboLabel.isPresent()) {
            if (turboLabel.get().isExclusive() && !resultList.get(name)) {
                String group = turboLabel.get().getGroup().get();
                allLabels
                        .stream()
                        .filter(TurboLabel::isExclusive)
                        .filter(label -> label.getGroup().get().equals(group))
                        .forEach(label -> resultList.put(label.getActualName(), false));
                resultList.put(name, true);
            } else {
                resultList.put(name, !resultList.get(name));
            }
        }
    }

    private void updateTopLabels() {
        topLabels.clear();
        allLabels.forEach(label -> {
            if (resultList.get(label.getActualName())) {
                topLabels.add(new PickerLabel(label, this));
            }
        });
    }

    private void updateBottomLabels(String match) {
        List<PickerLabel> matchedLabels = allLabels
                .stream()
                .filter(label -> label.getActualName().toLowerCase().contains(match))
                .map(label -> new PickerLabel(label, this))
                .collect(Collectors.toList());
        List<PickerLabel> selectedLabels = matchedLabels.stream()
                .filter(label -> resultList.get(label.getActualName()))
                .map(label -> {
                    label.setIsSelected(true);
                    return label;
                })
                .collect(Collectors.toList());
        List<PickerLabel> notSelectedLabels = matchedLabels.stream()
                .filter(label -> !resultList.get(label.getActualName())).collect(Collectors.toList());
        if (match.isEmpty()) {
            bottomLabels = notSelectedLabels;
        } else {
            bottomLabels = Stream.of(selectedLabels, notSelectedLabels)
                    .flatMap(Collection::stream).collect(Collectors.toList());
        }
        if (!bottomLabels.isEmpty()) {
            bottomLabels.get(0).setIsHighlighted(true);
            if (bottomLabels.size() > 1) {
                for (int i = 1; i < bottomLabels.size(); i++) {
                    bottomLabels.get(i).setIsHighlighted(false);
                }
            }
        }
    }

    private void moveHighlightOnLabel(boolean isDown) {
        for (int i = 0; i < bottomLabels.size(); i++) {
            if (bottomLabels.get(i).isHighlighted()) {
                if (isDown && i < bottomLabels.size() - 1) {
                    bottomLabels.get(i).setIsHighlighted(false);
                    bottomLabels.get(i + 1).setIsHighlighted(true);
                } else if (!isDown && i > 0) {
                    bottomLabels.get(i - 1).setIsHighlighted(true);
                    bottomLabels.get(i).setIsHighlighted(false);
                }
                break;
            }
        }
    }

}
