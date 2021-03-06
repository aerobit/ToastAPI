package jaci.openrio.toast.core.command;

import jaci.openrio.toast.core.Toast;
import jaci.openrio.toast.core.ToastBootstrap;
import jaci.openrio.toast.core.command.cmd.CommandGroovyScript;
import jaci.openrio.toast.core.command.cmd.CommandThreadPool;
import jaci.openrio.toast.core.command.cmd.CommandUSB;

import java.util.List;
import java.util.Scanner;
import java.util.Vector;
import java.util.function.Function;

/**
 * The main pipeline for commands. This bus handles registering and invocation of commands. This allows for
 * robot functions to be triggered remotely, or locally, based on a message.
 *
 * @see jaci.openrio.toast.core.command.AbstractCommand
 * @see jaci.openrio.toast.core.command.FuzzyCommand
 *
 * @author Jaci
 */
public class CommandBus {

    private static List<AbstractCommand> commands = new Vector<>();
    private static List<FuzzyCommand> parsers = new Vector<>();

    public static void init() {
        if (ToastBootstrap.isSimulation)
            setupSim();

        registerNatives();
    }

    private static void registerNatives() {
        registerCommand(new CommandGroovyScript());
        registerCommand(new CommandUSB());
        registerCommand(new CommandThreadPool());
    }

    /**
     * Parse the given message and invoke any commands matching it
     */
    public static void parseMessage(String message) {
        try {
            if (messageRequested()) {
                synchronized (requestLock) {
                    requestLock.notify();
                }
                return;
            }
        } catch (Exception e) {
            Toast.log().error("Could not do Custom Message implementation -- Falling back to regular command bus");
            Toast.log().exception(e);
        }

        inCommand = true;
        String[] split = message.split(" ");
        for (AbstractCommand command : commands) {
            if (split[0].equals(command.getCommandName())) {
                String[] newSplit = new String[split.length - 1];
                System.arraycopy(split, 1, newSplit, 0, split.length-1);
                command.invokeCommand(newSplit.length, newSplit, message);
            }
        }

        for (FuzzyCommand command : parsers) {
            if (command.shouldInvoke(message))
                command.invokeCommand(message);
        }
        inCommand = false;
    }

    /**
     * Register a {@link jaci.openrio.toast.core.command.FuzzyCommand} on the bus
     */
    public static void registerCommand(FuzzyCommand command) {
        parsers.add(command);
    }

    /**
     * Register a {@link jaci.openrio.toast.core.command.AbstractCommand} on the bus
     */
    public static void registerCommand(AbstractCommand command) {
        commands.add(command);
    }

    static boolean nextLineRequested = false;
    static boolean inCommand = false;
    static Scanner scanner;
    static String ln;
    static final Object requestLock = new Object();
    static Thread t;

    /**
     * Will bypass the CommandBus and give the next Console line or Command Message to you. Use this
     * to get data from the user. Keep in mind this is a blocking method.
     *
     * @throws InterruptedException Something wrong happened. This should never trigger
     */
    public static String requestNextMessage() throws InterruptedException {
        if (inCommand) {
            ln = scanner.nextLine();
            return ln;
        }
        synchronized (requestLock) {
            nextLineRequested = true;
            requestLock.wait();
            nextLineRequested = false;
            return ln;
        }
    }

    /**
     * Returns true if the {@link #requestNextMessage()} has been called
     */
    public static boolean messageRequested() {
        return nextLineRequested;
    }

    private static void setupSim() {
        scanner = new Scanner(System.in);
        t = new Thread() {
            public void run() {
                Thread.currentThread().setName("Toast|Sim");
                try {
                    while (Thread.currentThread().isAlive()) {
                        ln = scanner.nextLine();
                        parseMessage(ln);
                    }
                } catch (Exception e) {}
            };
        };
        t.start();
    }

}
