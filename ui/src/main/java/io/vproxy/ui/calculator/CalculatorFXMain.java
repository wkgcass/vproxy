package io.vproxy.ui.calculator;

import io.vproxy.vfx.control.scroll.ScrollDirection;
import io.vproxy.vfx.control.scroll.VScrollPane;
import io.vproxy.vfx.theme.Theme;
import io.vproxy.vfx.ui.button.FusionButton;
import io.vproxy.vfx.ui.pane.FusionPane;
import io.vproxy.vfx.ui.scene.VScene;
import io.vproxy.vfx.ui.scene.VSceneGroup;
import io.vproxy.vfx.ui.scene.VSceneShowMethod;
import io.vproxy.vfx.ui.stage.VStage;
import io.vproxy.vfx.util.FXUtils;
import javafx.application.Application;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;

import java.util.ArrayList;

public class CalculatorFXMain extends Application {
    @Override
    public void start(Stage primaryStage) {
        Theme.setTheme(new CustomTheme());

        var stage = new VStage(primaryStage);

        stage.getInitialScene().enableAutoContentWidthHeight();
        var root = stage.getInitialScene().getContentPane();

        var initialCalculatorScene = new IPv4CalculatorScene();
        var calculatorSceneGroup = new VSceneGroup(initialCalculatorScene);
        FXUtils.observeWidthHeight(root, calculatorSceneGroup.getNode(), 0, -80);
        root.getChildren().add(calculatorSceneGroup.getNode());

        var allScenes = new ArrayList<VScene>();
        allScenes.add(initialCalculatorScene);

        var controlPane = new FusionPane();
        controlPane.getNode().setLayoutX(10);
        controlPane.getNode().setPrefHeight(60);
        root.widthProperty().addListener((ob, old, now) -> {
            if (now == null) return;
            var w = now.doubleValue();
            controlPane.getNode().setPrefWidth(w - 10 - 10);
        });
        root.heightProperty().addListener((ob, old, now) -> {
            if (now == null) return;
            var h = now.doubleValue();
            controlPane.getNode().setLayoutY(h - 70);
        });
        root.getChildren().add(controlPane.getNode());

        var scrollPane = new VScrollPane(ScrollDirection.HORIZONTAL);
        controlPane.getContentPane().getChildren().add(scrollPane.getNode());
        FXUtils.observeWidthHeight(controlPane.getContentPane(), scrollPane.getNode());

        var controlContent = new HBox();
        controlContent.setSpacing(5);
        scrollPane.setContent(controlContent);
        FXUtils.observeHeight(scrollPane.getNode(), controlContent);

        var btn = new FusionButton("+");
        btn.setOnlyAnimateWhenNotClicked(true);
        controlContent.getChildren().addAll(initialCalculatorScene.sceneButton, btn);
        btn.setOnAction(e -> {
            var newScene = new IPv4CalculatorScene(calculatorSceneGroup, allScenes, controlContent.getChildren());
            allScenes.add(newScene);
            calculatorSceneGroup.addScene(newScene);
            controlContent.getChildren().add(controlContent.getChildren().size() - 1, newScene.sceneButton);
            calculatorSceneGroup.show(newScene, VSceneShowMethod.FROM_RIGHT);
        });

        initialCalculatorScene.sceneGroup = calculatorSceneGroup;
        initialCalculatorScene.allScenes = allScenes;
        initialCalculatorScene.buttonsParentChildren = controlContent.getChildren();

        stage.getStage().setWidth(850);
        stage.getStage().setHeight(600);
        stage.setTitle("IPv4 Network Calculator");
        stage.getStage().centerOnScreen();
        stage.getStage().show();
    }
}
