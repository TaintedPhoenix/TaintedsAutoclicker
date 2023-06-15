package io.github.TaintedPhoenix;
//Using Maven for dependencies
//Group id is my Github page

//com.tulskiy.keymaster
/*This is the library that provides native (global) hotkeys
 * that I use. I refer to it as Jkeymaster because that's its name on GitHub*/
import com.tulskiy.keymaster.common.Provider;

//javafx
/* JavaFX is the main library used in this project for both
 * data storage and UI. It is an advanced version of swing*/
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Point2D;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination.ModifierValue;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.robot.Robot;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.converter.IntegerStringConverter;

//org.json.simple
/* This library is run by google and I use it to save the hotkey data
 * to a json file, so it can be loaded on startup.*/
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

//java
/* Basic java imports, using swing mainly for robot and Jkeymaster
 * Because javafx's robot can only run on the application thread,
 * and I need to use a different thread so that the cpu intensive tasks
 * of autoclicking and keypressing don't freeze the application.
 * The other reason is that Jkeymaster only accepts swing/AWT classes.*/
import javax.swing.KeyStroke;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.function.UnaryOperator;


public class TaintedsAutoclicker extends Application
{
    private boolean promptOpen = false; //whether a key selection prompt is open

    private Provider provider; // the Jkeymaster provider used to register hotkeys that can be used on all platforms even when the app is out of focus
    private final ArrayList<KeyCode> keysToPress = new ArrayList<>(); //The keys to be pressed by the keypresser

    private KeyCodeCombination keypresserHotkey = new KeyCodeCombination(KeyCode.F7); // The hotkey to toggle the keypresser
    private KeyCodeCombination autoclickerHotkey = new KeyCodeCombination(KeyCode.F6); // The hotkey to toggle the autoclicker

    private boolean hotkeyHasMain = false; // Temp variable passed between functions
    private KeyCode hotkeyMain; // Temp variable passed between functions
    private final ArrayList<KeyCode> hotkeyTemp = new ArrayList<>(); // Temp variable passed between functions

    private Scene currentScene; // The currently active javaFX scene
    private Autoclicker autoclicker; //The currently updated autoclicker
    private Keypresser keypresser; // The currently updated keypresser

    //Launch the application
    @Override
    public void start(Stage primaryStage) throws Exception{ //Initialize the application and launch the UI
        primaryStage.setTitle("Autoclicker.exe");
        loadHotkeys();
        autoclickerScene(primaryStage);
        primaryStage.setAlwaysOnTop(true);
        provider = Provider.getCurrentProvider(false);
        primaryStage.setOnCloseRequest(windowEvent -> { // Make sure that everything stops when the window gets closed
            if (autoclicker != null && autoclicker.getClicking()) { autoclicker.stopAutoclicker(); }
            if (keypresser != null && keypresser.getPressing()) { keypresser.stopKeypresser(); }
            assert provider != null;
            provider.stop();
        });
        registerHotkeys();
        primaryStage.show();
    }

    @SuppressWarnings("unchecked")
    public void loadHotkeys()  { //Load the hotkeys from the json file
        JSONParser reader = new JSONParser();
        JSONObject data = new JSONObject();
        try {
            data = (JSONObject) reader.parse(new FileReader(getDataPath("hotkeys.json"))); //Reading the json file
        } catch (Exception ignored) {}
        ArrayList<Integer> autoclHotkey = new ArrayList<>();
        ArrayList<Integer> keypHotkey = new ArrayList<>();
        try {
            JSONArray aTemp = (JSONArray) data.get("autoclicker");
            JSONArray kTemp = (JSONArray) data.get("keypresser");

            aTemp.forEach(thing -> autoclHotkey.add(((Long) thing).intValue()));
            kTemp.forEach(thing -> keypHotkey.add(((Long) thing).intValue()));
        } catch (Exception ignored) { //If the json fails to be read for any reason, set the hotkeys back to default and continue
            autoclHotkey.add(KeyCode.F6.getCode());
            keypHotkey.add(KeyCode.F7.getCode());
        }
        autoclickerHotkey = assembleCombo(autoclHotkey);
        keypresserHotkey = assembleCombo(keypHotkey); //turning it into the correct format
    }

    public boolean checkDir() { //Make sure the directory exists
        File dir = new File(getDataPath());
        if (!dir.exists()) return dir.mkdir(); //If it doesn't, make it, or if the structure doesn't exist at all, (like on replit) return false, so we know not to try and save anything there.
        return true;
    }



    public void saveHotkeys() { //Write the hotkeys to json file
        if(!checkDir()) {registerHotkeys();}
        else {
            HashMap<String, JSONArray> data = new HashMap<>();
            data.put("autoclicker", simplifyKeycombo(autoclickerHotkey));
            data.put("keypresser", simplifyKeycombo(keypresserHotkey));
            try {
                FileWriter file = new FileWriter(getDataPath("hotkeys.json"));
                file.write(new JSONObject(data).toJSONString()); //write the JSON data to the file
                file.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            registerHotkeys();
        }
    }

    public String getDataPath() { //Get the data path for whatever os is currently in use, does not work on replit hence why saving does not work on Replit.
        if (com.sun.jna.Platform.isWindows()) return System.getenv("APPDATA") + File.separator + "TaintedsAutoclicker";
        else if (com.sun.jna.Platform.isLinux() || com.sun.jna.Platform.isMac()) return System.getProperty("user.home") + File.separator + ".TaintedsAutoclicker";
        else return System.getProperty("user.home") + File.separator + ".TaintedsAutoclicker";
    }

    public String getDataPath(String filename) {
        return getDataPath() + File.separator + filename;
    }

    public void registerHotkeys() { //Register the hotkeys with Jkeymaster so the events are triggered when they are activated
        if (provider == null) provider = Provider.getCurrentProvider(false);
        provider.reset(); //Reset the list of hotkeys
        provider.register(convertKeycombo(autoclickerHotkey), hotKey -> { //Register autoclicker hotkey and add a listener to toggle the autoclicker
            if (promptOpen) return;
            if (currentScene.lookup("#AC_button_keypresser") == null) return;
            if (autoclicker == null || !autoclicker.getClicking()) {
                getAutoclicker(currentScene);
                currentScene.lookup("#AC_button_start").setDisable(true);
                currentScene.lookup("#AC_button_stop").setDisable(false);
                currentScene.lookup("#AC_button_keypresser").setDisable(true);
                autoclicker.start();
            } else {
                autoclicker.stopAutoclicker();
                currentScene.lookup("#AC_button_start").setDisable(false);
                currentScene.lookup("#AC_button_stop").setDisable(true);
                currentScene.lookup("#AC_button_keypresser").setDisable(false);
            }
        });
        provider.register(convertKeycombo(keypresserHotkey), hotKey -> { //Register keypresser hotkey and add a listener to toggle the autoclicker
            if (promptOpen) return;
            if (currentScene.lookup("#KP_button_autoclicker") == null) return;
            if (keypresser == null || !keypresser.getPressing()) {
                getKeypresser(currentScene);
                currentScene.lookup("#KP_button_start").setDisable(true);
                currentScene.lookup("#KP_button_stop").setDisable(false);
                currentScene.lookup("#KP_button_autoclicker").setDisable(true);
                keypresser.start();
            } else {
                keypresser.stopKeypresser();
                currentScene.lookup("#KP_button_stop").setDisable(true);
                currentScene.lookup("#KP_button_start").setDisable(false);
                currentScene.lookup("#KP_button_autoclicker").setDisable(false);
            }
        });

    }

    public KeyCodeCombination assembleCombo(ArrayList<Integer> keys) { //Take a list of keys and turn it into a keyCodeCombo (A more advanced list of keys)
        ModifierValue[] settings = {ModifierValue.UP, ModifierValue.UP ,ModifierValue.UP, ModifierValue.UP ,ModifierValue.UP};

        if (keys.contains(KeyCode.SHIFT.getCode())) {
            settings[0] = ModifierValue.DOWN;
            keys.remove((Integer) KeyCode.SHIFT.getCode());
        } if (keys.contains(KeyCode.ALT.getCode())) {
            settings[2] = ModifierValue.DOWN;
            keys.remove((Integer) KeyCode.ALT.getCode());
        } if (keys.contains(KeyCode.META.getCode())) {
            settings[3] = ModifierValue.DOWN;
            keys.remove((Integer) KeyCode.META.getCode());
        } else if (keys.contains(KeyCode.CONTROL.getCode())) {
            settings[1] = ModifierValue.DOWN;
            keys.remove((Integer) KeyCode.CONTROL.getCode());
        }

        KeyCode mainKey = getKeyCode(keys.get(0));

        return new KeyCodeCombination(mainKey, settings[0], settings[1], settings[2], settings[3], settings[4]);
    }

    public void autoclickerScene(Stage primaryStage) throws Exception{ //Show the autoclicker scene
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getClassLoader().getResource("main.fxml"))); //Load from main.fxml file
        Scene mainScene = new Scene(root, 520, 400);
        primaryStage.setScene(mainScene);
        Platform.runLater(root::requestFocus); //make sure that none of the fields are focused by default
        currentScene = mainScene;

        Button keypresser = (Button) mainScene.lookup("#AC_button_keypresser"); //setup event handlers when the buttons are pressed
        EventHandler<MouseEvent> keypButtonHandler = ev -> {
            if (promptOpen) return;
            System.out.print("true");
            try {
                keypressScene(primaryStage);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        keypresser.addEventFilter(MouseEvent.MOUSE_CLICKED, keypButtonHandler);

        Button startButton = (Button) mainScene.lookup("#AC_button_start");
        EventHandler<MouseEvent> handlerStart = mouseEvent -> {
            if (promptOpen) return;
            mainScene.lookup("#AC_button_stop").setDisable(false);
            startButton.setDisable(true);
            keypresser.setDisable(true);
            getAutoclicker(mainScene);
            autoclicker.start();
        };
        startButton.addEventFilter(MouseEvent.MOUSE_CLICKED, handlerStart);

        Button stopButton = (Button) mainScene.lookup("#AC_button_stop");
        stopButton.setDisable(true);
        EventHandler<MouseEvent> handlerStop = mouseEvent -> {
            if (promptOpen) return;
            mainScene.lookup("#AC_button_start").setDisable(false);
            stopButton.setDisable(true);
            assert autoclicker != null;
            autoclicker.stopAutoclicker();
            keypresser.setDisable(false);
        };
        stopButton.addEventFilter(MouseEvent.MOUSE_CLICKED, handlerStop);

        Button hotkeyButton = (Button) mainScene.lookup("#AC_button_setHotkey");
        EventHandler<MouseEvent> handlerHotkey = mouseEvent -> {
            if (promptOpen) return;
            try { showHotkeySelection("autoclicker"); } catch (Exception ignored) {}
        };
        hotkeyButton.addEventFilter(MouseEvent.MOUSE_CLICKED, handlerHotkey);

        //Variable Definitions
        ToggleGroup radioButtons = new ToggleGroup();
        ToggleGroup posRadioButtons = new ToggleGroup();

        RadioButton repeatTimesRadio = (RadioButton) mainScene.lookup("#AC_radioButton_repeatNum");
        RadioButton repeatForeverRadio = (RadioButton) mainScene.lookup("#AC_radioButton_repeatForever");
        TextField repeatTimes = (TextField) mainScene.lookup("#AC_numField_repeatAmount");

        RadioButton cPosRadio = (RadioButton) mainScene.lookup("#AC_radioButton_currentPosition");
        RadioButton sPosRadio = (RadioButton) mainScene.lookup("#AC_radioButton_pickLocation");
        Button pickLocButton = (Button) mainScene.lookup("#AC_button_pickCursorLocation");
        TextField cursorX = (TextField) mainScene.lookup("#AC_numField_cursorX");
        TextField cursorY = (TextField) mainScene.lookup("#AC_numField_cursorY");

        MenuButton clickTypeMenu = (MenuButton) mainScene.lookup("#AC_menuButton_clickType");
        MenuButton mouseButtonMenu = (MenuButton) mainScene.lookup("#AC_menuButton_mouseButton");

        TextField hoursTF = (TextField) mainScene.lookup("#AC_numField_hours");
        TextField minutesTF = (TextField) mainScene.lookup("#AC_numField_minutes");
        TextField secondsTF = (TextField) mainScene.lookup("#AC_numField_seconds");
        TextField msTF = (TextField) mainScene.lookup("#AC_numField_milliseconds");

        //Onready code execution
        repeatTimesRadio.setToggleGroup(radioButtons);
        repeatForeverRadio.setToggleGroup(radioButtons);
        repeatTimesRadio.setSelected(true);
        repeatTimes.setDisable(false);

        cPosRadio.setToggleGroup(posRadioButtons);
        sPosRadio.setToggleGroup(posRadioButtons);
        cPosRadio.setSelected(true);
        pickLocButton.setDisable(true);
        cursorX.setDisable(true);
        cursorY.setDisable(true);

        //format the numFields to accept only positive integers (except cursorX and cursorY fields)
        //Filter every numfield
        repeatTimes.setTextFormatter(posIntFormatter(100));
        cursorX.setTextFormatter(allIntFormatter());
        cursorY.setTextFormatter(allIntFormatter());
        hoursTF.setTextFormatter(posIntFormatter());
        minutesTF.setTextFormatter(posIntFormatter());
        secondsTF.setTextFormatter(posIntFormatter());
        msTF.setTextFormatter(posIntFormatter(1000));

        //Event Handlers
        pickLocButton.addEventFilter(MouseEvent.MOUSE_RELEASED, mouseEvent -> {
            Point2D coords = new Robot().getMousePosition();
            cursorX.setText(String.valueOf((int) coords.getX()));
            cursorY.setText(String.valueOf((int) coords.getY()));
        });

        mouseButtonMenu.getItems().forEach(item -> item.setOnAction(event -> mouseButtonMenu.setText(item.getText())));

        clickTypeMenu.getItems().forEach(item -> item.setOnAction(event -> clickTypeMenu.setText(item.getText())));

        //When repeat times is selected, undisables the associated number input
        repeatTimesRadio.selectedProperty().addListener((observableValue, previouslySelected, selected) -> repeatTimes.setDisable(!selected));

        //If selecting pos, undisable the features that go along with that
        sPosRadio.selectedProperty().addListener((observableValue, previouslySelected, selected) -> {
            if (selected) {
                pickLocButton.setDisable(false);
                cursorX.setDisable(false);
                cursorY.setDisable(false);

            } else {
                pickLocButton.setDisable(true);
                cursorX.setDisable(true);
                cursorY.setDisable(true);
            }
        });
    }

    public void keypressScene(Stage primaryStage) throws Exception{ //Show the keypresser scene
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/keypress.fxml")));
        Scene keypressScene = new Scene(root, 520, 400);
        primaryStage.setScene(keypressScene);
        currentScene = keypressScene;

        Platform.runLater(root::requestFocus); //make sure that none of the fields are focused by default
        //AutoclickerButton
        Button autocl = (Button) keypressScene.lookup("#KP_button_autoclicker");
        EventHandler<MouseEvent> autoclButtonHandler = ev -> {
            if (promptOpen) return;
            System.out.print("true");
            try {
                autoclickerScene(primaryStage);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        autocl.addEventFilter(MouseEvent.MOUSE_CLICKED, autoclButtonHandler);

        //HotkeyButton
        Button hotkeyButton = (Button) keypressScene.lookup("#KP_button_setHotkey"); //All of this stuff is just action handling
        EventHandler<MouseEvent> handlerHotkey = mouseEvent -> {
            if (promptOpen) return;
            try { showHotkeySelection("keypresser"); } catch (Exception ignored) {}
        };
        hotkeyButton.addEventFilter(MouseEvent.MOUSE_CLICKED, handlerHotkey);

        //SelectKeyButton
        Button selectKeyButton = (Button) keypressScene.lookup("#KP_button_keySetting"); //Doing things when the various buttons are activated
        EventHandler<MouseEvent> handlerSelectKey = mouseEvent -> {
            if (promptOpen) return;
            try { showKeySelection(); } catch (Exception ignored) {}
        };
        selectKeyButton.addEventFilter(MouseEvent.MOUSE_CLICKED, handlerSelectKey);

        //Variable Definitions

        ToggleGroup radioButtons = new ToggleGroup();

        Button startButton = (Button) keypressScene.lookup("#KP_button_start");
        Button stopButton = (Button) keypressScene.lookup("#KP_button_stop");
        RadioButton repeatTimesRadio = (RadioButton) keypressScene.lookup("#KP_radioButton_repeatNum");
        RadioButton repeatForeverRadio = (RadioButton) keypressScene.lookup("#KP_radioButton_repeatForever");
        TextField repeatTimes = (TextField) keypressScene.lookup("#KP_numField_repeatAmount");

        TextField hoursTF = (TextField) keypressScene.lookup("#KP_numField_hours");
        TextField minutesTF = (TextField) keypressScene.lookup("#KP_numField_minutes");
        TextField secondsTF = (TextField) keypressScene.lookup("#KP_numField_seconds");
        TextField msTF = (TextField) keypressScene.lookup("#KP_numField_milliseconds");

        //Onready code execution
        repeatTimesRadio.setToggleGroup(radioButtons);
        repeatForeverRadio.setToggleGroup(radioButtons);
        stopButton.setDisable(true);
        repeatTimesRadio.setSelected(true);
        repeatTimesRadio.setDisable(false);

        repeatTimes.setTextFormatter(posIntFormatter(100));
        hoursTF.setTextFormatter(posIntFormatter());
        minutesTF.setTextFormatter(posIntFormatter());
        secondsTF.setTextFormatter(posIntFormatter());
        msTF.setTextFormatter(posIntFormatter(1000));

        msTF.setOnInputMethodTextChanged(inputMethodEvent -> System.out.println("hello"));

        //Event Handlers
        startButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            startButton.setDisable(true);
            autocl.setDisable(true);
            stopButton.setDisable(false);
            getKeypresser(keypressScene);
            keypresser.start();
        });

        stopButton.addEventFilter(MouseEvent.MOUSE_CLICKED, event -> {
            keypresser.stopKeypresser();
            stopButton.setDisable(true);
            startButton.setDisable(false);
            autocl.setDisable(false);
        });

        //When repeat times is selected, undisables the associated number input
        repeatTimesRadio.selectedProperty().addListener((observableValue, aBoolean, t1) -> repeatTimes.setDisable(!t1));
    }

    public void getAutoclicker(Scene mainScene) { //Return an autoclicker class based on the current settings
        RadioButton repeatTimesRadio = (RadioButton) mainScene.lookup("#AC_radioButton_repeatNum");
        //RadioButton repeatForeverRadio = (RadioButton) mainScene.lookup("#AC_radioButton_repeatForever");
        TextField repeatTimes = (TextField) mainScene.lookup("#AC_numField_repeatAmount");

        //RadioButton cPosRadio = (RadioButton) mainScene.lookup("#AC_radioButton_currentPosition");
        RadioButton sPosRadio = (RadioButton) mainScene.lookup("#AC_radioButton_pickLocation");
        TextField cursorX = (TextField) mainScene.lookup("#AC_numField_cursorX");
        TextField cursorY = (TextField) mainScene.lookup("#AC_numField_cursorY");

        MenuButton clickTypeMenu = (MenuButton) mainScene.lookup("#AC_menuButton_clickType");
        MenuButton mouseButtonMenu = (MenuButton) mainScene.lookup("#AC_menuButton_mouseButton");

        TextField hoursTF = (TextField) mainScene.lookup("#AC_numField_hours");
        TextField minutesTF = (TextField) mainScene.lookup("#AC_numField_minutes");
        TextField secondsTF = (TextField) mainScene.lookup("#AC_numField_seconds");
        TextField msTF = (TextField) mainScene.lookup("#AC_numField_milliseconds");

        if (hoursTF.getText().equals("")) hoursTF.setText("0");
        if (minutesTF.getText().equals("")) minutesTF.setText("0");
        if (secondsTF.getText().equals("")) secondsTF.setText("0");
        if (msTF.getText().equals("")) msTF.setText("1");

        int clickInterval = Integer.parseInt(msTF.getText()) + (Integer.parseInt(secondsTF.getText()) * 1000) + (Integer.parseInt(minutesTF.getText()) * 1000 * 60) + (Integer.parseInt(hoursTF.getText()) * 1000 * 60 * 60);
        boolean doubleClick = clickTypeMenu.getText().equals("Double");
        boolean rightClick = mouseButtonMenu.getText().equals("Right");
        boolean repeatForever = true;
        int repeats = 0;
        if (repeatTimesRadio.isSelected()) {
            repeatForever = false;
            repeats = Integer.parseInt(repeatTimes.getText());
        }
        boolean currentPos = true;
        int[] cursorPos = {0, 0};
        if (sPosRadio.isSelected()) {
            currentPos = false;
            cursorPos[0] = Integer.parseInt(cursorX.getText());
            cursorPos[1] = Integer.parseInt(cursorY.getText());
        }
        if (clickInterval < 1) clickInterval = 1;

        autoclicker =  new Autoclicker(clickInterval, repeats, cursorPos, repeatForever, currentPos, rightClick, doubleClick, this);
    }

    public void getKeypresser(Scene mainScene) { // Get a keypresser class based on the current settings
        RadioButton repeatTimesRadio = (RadioButton) mainScene.lookup("#KP_radioButton_repeatNum");
        //RadioButton repeatForeverRadio = (RadioButton) mainScene.lookup("#KP_radioButton_repeatForever");
        TextField repeatTimes = (TextField) mainScene.lookup("#KP_numField_repeatAmount");

        TextField hoursTF = (TextField) mainScene.lookup("#KP_numField_hours");
        TextField minutesTF = (TextField) mainScene.lookup("#KP_numField_minutes");
        TextField secondsTF = (TextField) mainScene.lookup("#KP_numField_seconds");
        TextField msTF = (TextField) mainScene.lookup("#KP_numField_milliseconds");

        if (hoursTF.getText().equals("")) hoursTF.setText("0");
        if (minutesTF.getText().equals("")) minutesTF.setText("0");
        if (secondsTF.getText().equals("")) secondsTF.setText("0");
        if (msTF.getText().equals("")) msTF.setText("5");

        int pressInterval = Integer.parseInt(msTF.getText()) + (Integer.parseInt(secondsTF.getText()) * 1000) + (Integer.parseInt(minutesTF.getText()) * 1000 * 60) + (Integer.parseInt(hoursTF.getText()) * 1000 * 60 * 60);
        boolean repeatForever = true;
        int repeats = 0;
        if (repeatTimesRadio.isSelected()) {
            repeatForever = false;
            repeats = Integer.parseInt(repeatTimes.getText());
        }
        if (pressInterval < 5) pressInterval = 5;

        keypresser =  new Keypresser(pressInterval, keysToPress, repeats, repeatForever, this );
    }

    public void clickDone() { //Callback once the clicking ends (if repeat forever is disabled)
        currentScene.lookup("#AC_button_start").setDisable(false);
        currentScene.lookup("#AC_button_stop").setDisable(true);
        assert autoclicker != null;
        autoclicker.stopAutoclicker();
        currentScene.lookup("#AC_button_keypresser").setDisable(false);
    }

    public void keypressDone() { //Callback once the pressing ends (if repeat forever is disabled)
        currentScene.lookup("#KP_button_start").setDisable(false);
        currentScene.lookup("#KP_button_stop").setDisable(true);
        assert keypresser != null;
        keypresser.stopKeypresser();
        currentScene.lookup("autoclicker").setDisable(false);
    }

    public void showHotkeySelection(String type) throws Exception{ //Show the prompt for choosing a new hotkey for either autoclicker or keypresser
        if (promptOpen) return;
        promptOpen = true;
        KeyCodeCombination hotkey;
        if (type.equals("autoclicker")) {
            hotkey = autoclickerHotkey;
        } else {
            hotkey = keypresserHotkey;
        }
        Stage newStage = new Stage();
        newStage.setTitle(" ");
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/keyselectionPopup.fxml"))); //Load keyselectionPopup.fxml scene
        Scene hotkeyScene = new Scene(root, 200, 100);
        newStage.setScene(hotkeyScene);
        newStage.setAlwaysOnTop(true);
        newStage.setOnCloseRequest(event -> promptOpen = false);
        newStage.initModality(Modality.APPLICATION_MODAL);
        Label infoLabel = (Label) hotkeyScene.lookup("#HS_label_info");
        if (hotkey != null) {
            infoLabel.setText(hotkey.getName());
        } else {
            infoLabel.setText("None");
        }
        newStage.show();


        Button okButton = (Button) hotkeyScene.lookup("#HS_button_ok");
        EventHandler<MouseEvent> okHandler = mouseEvent -> {
            promptOpen = false;
            if (hotkeyHasMain) {
                ModifierValue[] settings = {ModifierValue.UP, ModifierValue.UP ,ModifierValue.UP, ModifierValue.UP ,ModifierValue.UP};

                if (hotkeyTemp.contains(KeyCode.SHIFT)) {
                    settings[0] = ModifierValue.DOWN;
                } if (hotkeyTemp.contains(KeyCode.ALT)) {
                    settings[2] = ModifierValue.DOWN;
                } if (hotkeyTemp.contains(KeyCode.META)) {
                    settings[3] = ModifierValue.DOWN;
                } else if (hotkeyTemp.contains(KeyCode.CONTROL)) {
                    settings[1] = ModifierValue.DOWN;
                }
                KeyCodeCombination output = new KeyCodeCombination(hotkeyMain, settings[0], settings[1], settings[2], settings[3], settings[4]);

                if (type.equals("autoclicker")) {
                    autoclickerHotkey = output;
                } else {
                    keypresserHotkey = output;
                }
            }
            hotkeyTemp.clear();
            hotkeyHasMain = false;
            saveHotkeys();
            newStage.close();
        };
        okButton.addEventFilter(MouseEvent.MOUSE_CLICKED, okHandler);

        EventHandler<KeyEvent> keyInputHandler = event -> { //read in key inputs
            boolean keyAdded = false;
            if (!hotkeyTemp.contains(event.getCode())) {
                if (event.getCode().isModifierKey()) {
                    hotkeyTemp.add(event.getCode());
                    keyAdded = true;
                } else if (!hotkeyHasMain) {
                    hotkeyHasMain = true;
                    hotkeyMain = event.getCode();
                    keyAdded = true;
                }
            }
            if (keyAdded) {
                if (infoLabel.getText().equals("Listening...")) {
                    infoLabel.setText(event.getCode().getName());
                } else {
                    infoLabel.setText(infoLabel.getText() + " + " + event.getCode().getName());
                }
            }
        };

        Button startButton = (Button) hotkeyScene.lookup("#HS_button_selectKeys");

        EventHandler<MouseEvent> startHandler = mouseEvent -> {
            hotkeyTemp.clear();
            hotkeyHasMain = false;
            infoLabel.setText("Listening...");
            hotkeyScene.addEventFilter(KeyEvent.KEY_PRESSED, keyInputHandler);

        };
        startButton.addEventFilter(MouseEvent.MOUSE_CLICKED, startHandler);
    }

    public void showKeySelection() throws Exception{ // Show prompt to select which keys the keypresser should press
        if (promptOpen) return;
        promptOpen = true;
        Stage newStage = new Stage();
        newStage.setTitle(" ");
        Parent root = FXMLLoader.load(Objects.requireNonNull(getClass().getResource("/keypressSelectionPopup.fxml"))); //load scene from keypressSelectionPopup.fxml
        Scene keyScene = new Scene(root, 200, 130);
        newStage.setScene(keyScene);
        newStage.setAlwaysOnTop(true);
        newStage.setOnCloseRequest(event -> promptOpen = false);

        newStage.initModality(Modality.APPLICATION_MODAL);
        Label infoLabel = (Label) keyScene.lookup("#KS_label_info");
        if (keysToPress.size() > 0) {
            infoLabel.setText(keysToPress.get(0).getName());
        } else {
            infoLabel.setText("None");
        }
        for (int i = 1; i < keysToPress.size(); i++) {
            infoLabel.setText(infoLabel.getText() + " + " + keysToPress.get(i).getName());
        }
        newStage.show();

        Button okButton = (Button) keyScene.lookup("#KS_button_ok");
        EventHandler<MouseEvent> okHandler = mouseEvent -> {
            promptOpen = false;
            newStage.close();
        };
        okButton.addEventFilter(MouseEvent.MOUSE_CLICKED, okHandler);

        EventHandler<KeyEvent> keyInputHandler = event -> { //Listen for key input
            if (!(keysToPress.contains(event.getCode()))) {
                keysToPress.add(event.getCode());
                if (infoLabel.getText().equals("Listening...")) {
                    infoLabel.setText(event.getCode().getName());
                } else {
                    infoLabel.setText(infoLabel.getText() + " + " + event.getCode().getName());
                }
            }
        };

        Button startButton = (Button) keyScene.lookup("#KS_button_start");

        EventHandler<MouseEvent> startHandler = mouseEvent -> {
            startButton.setDisable(true);
            keysToPress.clear();
            infoLabel.setText("Listening...");
            keyScene.addEventFilter(KeyEvent.KEY_PRESSED, keyInputHandler);

            keyScene.lookup("#KS_button_stop").setDisable(false);
        };
        startButton.addEventFilter(MouseEvent.MOUSE_CLICKED, startHandler);

        Button stopButton = (Button) keyScene.lookup("#KS_button_stop");
        stopButton.setDisable(true);
        stopButton.addEventFilter(MouseEvent.MOUSE_CLICKED, mouseEvent -> {
            stopButton.setDisable(true);
            keyScene.removeEventFilter(KeyEvent.KEY_PRESSED, keyInputHandler);
            startButton.setDisable(false);
        });
    }

    public int convertModifiers(KeyCodeCombination keys) { //Converts javaFX KeyCodeCombo modifiers to javaAWT modifier enum (for Jkeymaster)
        int total = 0;
        if (keys.getShift().equals(ModifierValue.DOWN)) { total += java.awt.event.InputEvent.SHIFT_DOWN_MASK; }
        if (keys.getControl().equals(ModifierValue.DOWN)) { total += java.awt.event.InputEvent.CTRL_DOWN_MASK; }
        if (keys.getMeta().equals(ModifierValue.DOWN)) { total += java.awt.event.InputEvent.META_DOWN_MASK; }
        if (keys.getAlt().equals(ModifierValue.DOWN)) { total += java.awt.event.InputEvent.ALT_DOWN_MASK; }
        return total;
    }

    public KeyStroke convertKeycombo (KeyCodeCombination keys) { // Converts javaFX KeyCode to javaSwing KeyStroke (for Jkeymaster)
        //noinspection MagicConstant
        return KeyStroke.getKeyStroke(keys.getCode().getCode(), convertModifiers(keys));
    }

    @SuppressWarnings("unchecked")
    public JSONArray simplifyKeycombo(KeyCodeCombination hotkey) { // Converts javaFX KeyCodeCombo to JSONArray to be written to file
        JSONArray temp = new JSONArray();
        temp.add(hotkey.getCode().getCode());
        if (hotkey.getAlt().equals(ModifierValue.DOWN)) {
            temp.add(KeyCode.ALT.getCode());
        }
        if (hotkey.getControl().equals(ModifierValue.DOWN)) {
            temp.add(KeyCode.CONTROL.getCode());
        }
        if (hotkey.getMeta().equals(ModifierValue.DOWN)) {
            temp.add(KeyCode.META.getCode());
        }
        if (hotkey.getShift().equals(ModifierValue.DOWN)) {
            temp.add(KeyCode.SHIFT.getCode());
        }
        if (hotkey.getShortcut().equals(ModifierValue.DOWN)) {
            temp.add(KeyCode.SHORTCUT.getCode());
        }

        return temp;
    }

    public KeyCode getKeyCode(int code) { //Converts an int representing a keycode from JavaAWT to a KeyCode class from JavaFX
        for ( KeyCode k : KeyCode.values()) {
            if (k.getCode() == code) {
                return k;
            }
        }
        return null;
    }

    public TextFormatter<Integer> posIntFormatter() {
        return  posIntFormatter(0);
    }

    public TextFormatter<Integer> posIntFormatter(int defaultValue) { //If applied to textfield it will make sure only positive integers can be inputted
        UnaryOperator<TextFormatter.Change> posIntegerFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("[0-9]*")) {
                return change;
            }
            return null;
        };

        IntegerStringConverter integerStringConverter = new IntegerStringConverter();

        return new TextFormatter<>(integerStringConverter, defaultValue, posIntegerFilter);
    }

    public TextFormatter<Integer> allIntFormatter() { //If applied to textfield it will allow only integers to be inputted
        IntegerStringConverter integerStringConverter = new IntegerStringConverter();
        UnaryOperator<TextFormatter.Change> allIntegerFilter = change -> {
            String newText = change.getControlNewText();
            if (newText.matches("-?([0-9]+)?")) {
                return change;
            } else if ("-".equals(change.getText())) {
                if (change.getControlText().startsWith("-")) {
                    change.setText("");
                    change.setRange(0, 1);
                    change.setCaretPosition(change.getCaretPosition()-2);
                    change.setAnchor(change.getAnchor()-2);
                } else {
                    change.setRange(0, 0);
                }
                return change;
            }
            return null;
        };


        return new TextFormatter<>(integerStringConverter, 0, allIntegerFilter);
    }

    public static void main(String[] args) { //run the application
        launch(args);
    }
}