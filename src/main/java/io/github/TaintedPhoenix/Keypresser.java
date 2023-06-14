package io.github.TaintedPhoenix;

import javafx.scene.input.KeyCode;
import java.awt.Robot;
import java.util.ArrayList;

public class Keypresser extends Thread {

    //Keypresser settings
    private final boolean repeatForever;
    private final int repeats;
    private int pressInterval;
    private final ArrayList<Integer> keys = new ArrayList<>();

    //Reference to the main.java for callback if required
    private final TaintedsAutoclicker callback;

    //Internals used for interclass communication
    private boolean pressing = false;
    private boolean stop = false;



    public Keypresser(int interval, ArrayList<KeyCode> keysRaw, int repeats, boolean repeatForever, TaintedsAutoclicker caller){ //constructor
        this.pressInterval = interval;
        this.repeats = repeats;
        this.repeatForever = repeatForever;
        this.callback = caller;
        if (this.pressInterval < 5) {
            this.pressInterval = 5;
        }
        for (KeyCode keyCode : keysRaw) {
            keys.add(keyCode.getCode());
        }
    }

    public void stopKeypresser() { //Interrupts the thread, stopping the keypresser
        stop = true;
        pressing = false;
        interrupt();
    }

    public boolean getPressing() {
        return this.pressing;
    }

    @Override
    @SuppressWarnings("all")
    public void run() { //Does the keypressing using JavaAWT Robot
        pressing = true;
        try {
            Robot robot = new Robot();
            if (repeatForever) {
                while (!stop && !interrupted()) {
                    try {
                        sleep(pressInterval);
                    } catch (InterruptedException er) {
                        stop = true; pressing = false;}
                    keys.forEach(robot::keyPress);
                    keys.forEach(robot::keyRelease);
                }
            } else {
                for (int i = 0; i < repeats && !stop && !interrupted(); i++) {
                    try {
                        sleep(pressInterval);
                    } catch (InterruptedException ignored) { stop = true; pressing = false;}
                    keys.forEach(robot::keyPress);
                    keys.forEach(robot::keyRelease);
                }
                Thread.sleep(50);
                callback.keypressDone();
                pressing = false;

            }
        } catch (Exception ignored) { pressing = false;}
    }
}
