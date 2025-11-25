package org.bdj;

import java.io.*;
import java.util.*;
import javax.tv.xlet.*;
import java.awt.BorderLayout;
import org.havi.ui.HScene;
import org.havi.ui.HSceneFactory;
import org.dvb.event.UserEvent;
import org.dvb.event.EventManager;
import org.dvb.event.UserEventListener;
import org.dvb.event.UserEventRepository;
import org.bluray.ui.event.HRcEvent;
import org.bdj.sandbox.DisableSecurityManagerAction;
import org.bdj.external.*;

public class InitXlet implements Xlet, UserEventListener
{
    private static InitXlet instance;
    private EventQueue eq;
    private HScene scene;
    private Screen gui;
    private XletContext context;
    private static PrintStream console;
    private static final ArrayList messages = new ArrayList();
    // --- Inner Classes ---
    public static class EventQueue
    {
        private LinkedList l;
        int cnt = 0;
        EventQueue()
        {
            l = new LinkedList();
        }
        public synchronized void put(Object obj)
        {
            l.addLast(obj);
            cnt++;
        }
        public synchronized Object get()
        {
            if(cnt == 0)
                return null;
            Object o = l.getFirst();
            l.removeFirst();
            cnt--;
            return o;
        }
    }

    // --- Public Methods ---
    public void initXlet(XletContext context)
    {
        // Privilege escalation
        try {
            escalatePrivileges();
            initializeInstance(context);
            setupGUI();
            setupEventListeners();
            startExploitThread();

        } catch (Exception e) {
            Logger.log(console, "Initialization error: " + e.getMessage());
            printStackTrace(e);
        }
    }
    public void startXlet()
    {
        gui.setVisible(true);
        scene.setVisible(true);
        gui.requestFocus();
    }
    public void pauseXlet()
    {
        gui.setVisible(false);
    }
    public void destroyXlet(boolean unconditional)
    {
        scene.remove(gui);
        scene = null;
    }

    public void userEventReceived(UserEvent evt)
    {
        if (evt.getType() == HRcEvent.KEY_PRESSED)
        {
            switch (evt.getCode())
            {
                case Constants.BUTTON_U:
                    gui.top += 270;
                    scene.repaint();
                    return;
                case Constants.BUTTON_D:
                    gui.top -= 270;
                    scene.repaint();
                    return;
                default:
                    eq.put(new Integer(evt.getCode()));
            }
        }
    }
    public static void repaint()
    {
        instance.scene.repaint();
    }
    public static int pollInput()
    {
        Object ans = instance.eq.get();
        return (ans == null) ? 0 : ((Integer) ans).intValue();
    }
    // --- Private Methods ---
    private void escalatePrivileges() throws Exception
    {
        try {
            DisableSecurityManagerAction.execute();
            Logger.log(console, "Privilege escalation successful.");
        } catch (Exception e) {
            Logger.log(console, "Privilege escalation failed: " + e.getMessage());
            throw e;
        }
    }

    private void initializeInstance(XletContext context)
    {
        instance = this;
        this.context = context;
        this.eq = new EventQueue();
    }

    private void setupGUI()
    {
        scene = HSceneFactory.getInstance().getDefaultHScene();
        gui = new Screen(messages);
        gui.setSize(Constants.SCREEN_WIDTH, Constants.SCREEN_HEIGHT);
        scene.add(gui, BorderLayout.CENTER);
        scene.validate();
    }

    private void setupEventListeners()
    {
        UserEventRepository repo = new UserEventRepository("input");
        repo.addKey(Constants.BUTTON_X);
        repo.addKey(Constants.BUTTON_O);
        repo.addKey(Constants.BUTTON_U);
        repo.addKey(Constants.BUTTON_D);
        EventManager.getInstance().addUserEventListener(this, repo);
    }

    private void startExploitThread()
    {
        (new Thread() {
            public void run() {
                try {
                    scene.repaint();
                    console = new PrintStream(new MessagesOutputStream(messages, scene));
                    logStartupInfo();
                    handleExploits();
                } catch (Throwable e) {
                    Logger.log(console, "Unexpected error: " + e.getMessage());
                    printStackTrace(e);
                    scene.repaint();
                }
            }
        }).start();
    }

    private void handleExploits() {
        System.gc(); // this workaround somehow makes Call API working
        if (System.getSecurityManager() != null) {
            Logger.log(console, "Privilege escalation failure, unsupported firmware?");
            return;
        }
    
        Kernel.initializeKernelOffsets();
        String firmwareVersion = Helper.getCurrentFirmwareVersion();
        Logger.log(console, "Firmware: " + firmwareVersion);
    
        if (!KernelOffset.hasPS4Offsets()) {
            Logger.log(console, "Unsupported Firmware");
            return;
        }

        boolean lapseSupported = (!firmwareVersion.equals("12.50") && !firmwareVersion.equals("12.52"));
        runExploitLoop(lapseSupported);
    }
    
    private void runExploitLoop(boolean lapseSupported)
    {
        int lapseFailCount = 0;
    
        while (true) {
            int input = waitForUserInput(lapseSupported);
    
            if (runExploit(lapseSupported, input, console, lapseFailCount)) {
                break;
            }
    
            if (input == Constants.BUTTON_X && lapseSupported) {
                lapseFailCount++;
            }
        }
    }

    private boolean runExploit(boolean lapseSupported, int button, PrintStream console, int lapseFailCount)
    {
        try {
            int result = (button == Constants.BUTTON_X && lapseSupported)
                ? org.bdj.external.Lapse.main(console)
                : org.bdj.external.Poops.main(console);

            if (result == 0) {
                Logger.log(console, "Success");
                return true;
            }

            if (button == Constants.BUTTON_X && lapseSupported && (result <= -6 || lapseFailCount >= 3)) {
                Logger.log(console, "Fatal fail(" + result + "), please REBOOT PS4");
                return true;
            }

            Logger.log(console, "Failed (" + result + "), but you can try again");
            return false;
        } catch (Exception e) {
            Logger.log(console, "Exception during exploit: " + e.getMessage());
            printStackTrace(e);
            return true;
        }
    }
    
    private int waitForUserInput(boolean lapseSupported)
    {
        int input = 0;
        Logger.log(console, "\nSelect the mode to run:");
        if (lapseSupported) {
            Logger.log(console, "* X = Lapse");
            Logger.log(console, "* O = Poops");
        } else {
            Logger.log(console, "* X = Poops");
        }
    
        while ((input != Constants.BUTTON_O || !lapseSupported) && input != Constants.BUTTON_X) {
            input = pollInput();
        }
        return input;
    }

    private void logStartupInfo()
    {
        Logger.log(console, "Hen Loader LP v1.0, based on:");
        Logger.log(console, "- GoldHEN 2.4b18.7 by SiSTR0");
        Logger.log(console, "- poops code by theflow0");
        Logger.log(console, "- lapse code by Gezine");
        Logger.log(console, "- BDJ build environment by kimariin");
        Logger.log(console, "- java console by sleirsgoevy");
        Logger.log(console, "");
    }

    private void printStackTrace(Throwable e)
    {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        Logger.log(console, sw.toString());
    }
}
