package com.cmic.event.state;

public abstract class AbstractStateController {
    public ControllerMachine machine;

    public AbstractStateController(ControllerMachine machine) {
        this.machine = machine;
    }
}
