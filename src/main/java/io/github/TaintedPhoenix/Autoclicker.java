package io.github.TaintedPhoenix;

import java.awt.Robot;
import java.awt.event.InputEvent;

public class Autoclicker extends Thread {

    //Settings
    private final int[] cursorPos;
    private final boolean doubleClick;
    private final boolean rightClick;
    private final boolean repeatForever;
    private final int repeats;
    private final boolean currentPos;
    private int clickInterval;

    //Internals for inter class communication
    private boolean stop = false;
    private boolean clicking = false;

    //For callback if necessary
    private final TaintedsAutoclicker callback;



    public Autoclicker(int interval, int repeats, int[] cursorPos, boolean repeatForever, boolean currentPos, boolean rightClick, boolean doubleClick, TaintedsAutoclicker caller){ // Constructor
        this.clickInterval = interval;
        this.repeats = repeats;
        this.cursorPos = cursorPos;
        this.repeatForever = repeatForever;
        this.currentPos = currentPos;
        this.rightClick = rightClick;
        this.doubleClick = doubleClick;
        this.callback = caller;
        if (this.clickInterval < 1) {
            this.clickInterval = 1;
        }
    }

    public void stopAutoclicker() { //Interrupts the thread stopping the autoclicker
        stop = true;
        clicking = false;
        interrupt();
    }

    public boolean getClicking() {
        return this.clicking;
    }

    @Override
    @SuppressWarnings("all")
    public void run() { //Does the clicking
        clicking = true;
        try {
            Robot robot = new Robot();
            if (!currentPos) { //Moves the mouse to the selected position if option enabled
                robot.mouseMove(cursorPos[0], cursorPos[1]);
            }
            int mouseButton = InputEvent.BUTTON1_DOWN_MASK;
            if (rightClick) {mouseButton = InputEvent.BUTTON2_DOWN_MASK;} //Selects the right input button
            robot.mousePress(mouseButton);
            robot.mouseRelease(mouseButton); //clicks for the first time
            if (doubleClick) {
                robot.mousePress(mouseButton);
                robot.mouseRelease(mouseButton);
            }

            if (repeatForever) { //loop through clicking until stopped or the amount of repeats are finished
                if (doubleClick) {
                    while (!stop && !interrupted()) {
                        try {
                            sleep(clickInterval);
                        } catch (InterruptedException er) {
                            stop = true; clicking = false;
                        }
                        if (!currentPos) {
                            robot.mouseMove(cursorPos[0], cursorPos[1]);
                        }
                        robot.mousePress(mouseButton);
                        robot.mouseRelease(mouseButton);
                        robot.mousePress(mouseButton);
                        robot.mouseRelease(mouseButton);

                    }
                } else {
                    while (!stop && !interrupted()) {
                        try {
                            sleep(clickInterval);
                        } catch (InterruptedException er) {
                            stop = true; clicking = false;
                        }
                        if (!currentPos) {
                            robot.mouseMove(cursorPos[0], cursorPos[1]);
                        }
                        robot.mousePress(mouseButton);
                        robot.mouseRelease(mouseButton);
                    }
                }
            } else {
                if (doubleClick) {
                    for (int i = 0; i < repeats && !stop && !interrupted(); i++) {
                        try {
                            sleep(clickInterval);
                        } catch (InterruptedException ignored) {
                            stop = true; clicking = false;
                        }
                        if (!currentPos) {
                            robot.mouseMove(cursorPos[0], cursorPos[1]);
                        }
                        robot.mousePress(mouseButton);
                        robot.mouseRelease(mouseButton);
                        robot.mousePress(mouseButton);
                        robot.mouseRelease(mouseButton);

                    }
                } else {
                    for (int i = 0; i < repeats && !stop && !interrupted(); i++) {
                        try {
                            sleep(clickInterval);
                        } catch (InterruptedException ignored) {
                            stop = true; clicking = false;
                        }
                        if (!currentPos) {
                            robot.mouseMove(cursorPos[0], cursorPos[1]);
                        }
                        robot.mousePress(mouseButton);
                        robot.mouseRelease(mouseButton);
                    }
                }
                Thread.sleep(50);
                callback.clickDone(); //call a function in the app class to let it know this is finished
                clicking = false;

            }
        } catch (Exception ignored) { clicking = false;}
    }
}
