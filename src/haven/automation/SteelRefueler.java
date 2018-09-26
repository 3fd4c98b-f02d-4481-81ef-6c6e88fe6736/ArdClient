package haven.automation;

import static haven.OCache.posres;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import haven.*;
import haven.purus.BotUtils;
import haven.purus.pbot.PBotAPI;


public class SteelRefueler extends Window implements GobSelectCallback {
    private static final Text.Foundry infof = new Text.Foundry(Text.sans, 10).aa(true);
    private List<Gob> crucibles = new ArrayList<>();
    private List<Gob> stockpiles = new ArrayList<>();
    private List<Double> completepercent = new ArrayList<>();
    private final Label lblc, lbls;
    public boolean terminate = false;
    private Button clearbtn, runbtn, stopbtn, areaSelBtn;
    private static final int TIMEOUT = 2000;
    private Coord selectedAreaA, selectedAreaB;
    private Thread selectingarea;
    private int reportouttimer = 1;
    private double highest, lowest = 0;
    List<WItem> ItemList;
    private static final int HAND_DELAY = 8;
    private static final int SLEEP = 30 * 60 * 1000; // 30 min
    private Thread runner;


    public SteelRefueler() {
        super(new Coord(270, 180), "Steel Refueler");

        final Label lbl = new Label("Click select and select your area with crucibles/stockpiles.", infof);
        add(lbl, new Coord(30, 20));

        Label lblctxt = new Label("Crucibles Selected:", infof);
        add(lblctxt, new Coord(15, 60));
        lblc = new Label("0", Text.num12boldFnd, Color.WHITE);
        add(lblc, new Coord(110, 58));

        Label lblstxt = new Label("Stockpiles Selected:", infof);
        add(lblstxt, new Coord(135, 60));
        lbls = new Label("0", Text.num12boldFnd, Color.WHITE);
        add(lbls, new Coord(235, 58));

        clearbtn = new Button(140, "Clear Selection") {
            @Override
            public void click() {
                crucibles.clear();
                stockpiles.clear();
                lblc.settext(crucibles.size() + "");
                lbls.settext(stockpiles.size() + "");
            }
        };
        add(clearbtn, new Coord(65, 70));

        runbtn = new Button(140, "Run") {
            @Override
            public void click() {
                if (crucibles.size() == 0) {
                    gameui().error("No crucibles selected.");
                    return;
                } else if (stockpiles.size() == 0) {
                    gameui().error("No stockpiles selected.");
                    return;
                }

                this.hide();
                cbtn.hide();
                clearbtn.hide();
                stopbtn.show();
                terminate = false;

                runner = new Thread(new Runner(), "Steel Refueler");
                runner.start();
            }
        };
        add(runbtn, new Coord(65, 120));

        stopbtn = new Button(140, "Stop") {
            @Override
            public void click() {
                terminate = true;
                // TODO: terminate PF
                this.hide();
                runbtn.show();
                clearbtn.show();
                cbtn.show();
            }
        };
        stopbtn.hide();
        add(stopbtn, new Coord(65, 120));
        areaSelBtn = new Button(140, "Select") {
            @Override
            public void click() {
                selectingarea = new Thread(new SteelRefueler.selectingarea(), "Steel Refueler");
                selectingarea.start();
            }
        };
        add(areaSelBtn, new Coord(65, 150));
    }

    private class Runner implements Runnable {
        @Override
        public void run() {
            GameUI gui = gameui();
            while (!terminate) {
                cloop:
                for (Gob c : crucibles) {
                    // take fuel from stockpiles if we don't have enough in the inventory
                  //  int availableFuelCoal = gui.maininv.getItemPartialCount("Coal");
                   // int availableFuelBlock = gui.maininv.getItemPartialCount("Block");
                    int availableFuelBranch = gui.maininv.getItemPartialCount("Branch");
                    if (availableFuelBranch < 18)
                        getfuel();

                    // find one piece of fuel in the inventory
                    WItem fuel = gui.maininv.getItemPartial("Branch");
                  //  if (fuel == null)
                     //   continue;

                    int fuelticks = 5; // branch

                    // navigate to crucible
                  //  System.out.println("Before move to crucible");
                  //  gui.map.pfRightClick(c, -1, 3, 1, null);
                    PBotAPI.pfRightClick(c,0);
                    try {
                        gui.map.pfthread.join();
                    } catch (InterruptedException e) {
                        return;
                    }
                    //System.out.println("After move to crucible");

                    if (terminate)
                        return;

                    // get crucible fuel status
                    // wait for the window. really ugly but oh well...
                    try {
                        Thread.sleep(TIMEOUT);
                    } catch (InterruptedException e) {
                        return;
                    }
                   // System.out.println("Grab Steelbox window");
                    Window cwnd = gui.getwnd("Steelbox");
                    if (cwnd == null) {
                       System.out.println("Window null, skipping");
                        continue;
                    }
                    VMeter vm = cwnd.getchild(VMeter.class);
                    //grabs percentage steel is finished
                    for (Widget w = cwnd.lchild; w != null; w = w.prev) {
                        if (w instanceof Inventory)
                            ItemList = BotUtils.getInventoryContents((Inventory) w);
                        try {
                            for (WItem loop : ItemList)
                                if (loop.itemmeter.get() > 0)
                                    completepercent.add((loop.itemmeter.get()*100));
                        } catch (NullPointerException qqq) {
                        }
                    }

                    if (vm == null)
                        continue;

                    if (vm.amount > (100 - fuelticks))
                        continue;

                    int fueltoload = (100 - vm.amount) / fuelticks;

                    // take fuel
                   // System.out.println("Take fuel");
                    fuel.item.wdgmsg("take", new Coord(fuel.item.sz.x / 2, fuel.item.sz.y / 2));
                    int timeout = 0;
                    while (gui.hand.isEmpty()) {
                        timeout += HAND_DELAY;
                        if (timeout >= TIMEOUT)
                            continue cloop;
                        try {
                            Thread.sleep(HAND_DELAY);
                        } catch (InterruptedException e) {
                            return;
                        }
                    }
                    int curbranches = gui.maininv.getItemPartialCount("Branch");
                    System.out.println("branches : "+curbranches);
                    System.out.println("Loading : "+fueltoload);
                    int freeslots = BotUtils.invFreeSlots();
                    for (; fueltoload > 0; fueltoload--) {

                        if (terminate)
                            return;
                        gui.map.wdgmsg("itemact", Coord.z, c.rc.floor(posres), fueltoload == 1 ? 0 : 1, 0, (int) c.id, c.rc.floor(posres), 0, -1);
                        while (true) {
                            WItem newfuel = gui.vhand;
                            if (newfuel != fuel && newfuel != null) {
                                fuel = newfuel;
                                break;
                            }

                            timeout += HAND_DELAY;
                            if (timeout >= TIMEOUT) {
                                System.out.println("Add fuel timeout");
                                break;
                            }
                            try {
                                Thread.sleep(HAND_DELAY);
                            } catch (InterruptedException e) {
                                return;
                            }
                        }
                    }

                    WItem hand = gui.vhand;
                    // if the crucible is full already we'll end up with a stockpile on the cursor
                    if (hand != null) {
                        gui.map.wdgmsg("place", Coord.z, 0, 3, 0);
                        Coord slot = BotUtils.getFreeInvSlot(BotUtils.playerInventory());
                        if (slot != null) {
                            BotUtils.dropItemToInventory(slot, BotUtils.playerInventory());
                            while (BotUtils.getItemAtHand() != null)
                                BotUtils.sleep(10);
                        }
                    }
                    System.out.println("Refueled with "+(curbranches - gui.maininv.getItemPartialCount("Branch")));
                    System.out.println("Branches in inv : "+gui.maininv.getItemPartialCount("Branch"));
                }
                Collections.sort(completepercent);
                lowest = completepercent.get(0);
                if(reportouttimer == 4)
                    reportouttimer = 1;
                if(reportouttimer == 1) {
                    if (Discord.jdalogin != null) {
                        for (Widget w = gui.chat.lchild; w != null; w = w.prev) {
                            if (w instanceof ChatUI.DiscordChannel) {
                                if (((ChatUI.DiscordChannel) w).name().contains("steel")) {
                                    ((ChatUI.DiscordChannel) w).send("!check");
                                    ((ChatUI.DiscordChannel) w).send("Lowest Complete % was : " + round(lowest, 2));
                                }
                            }
                        }
                    }
                }

                reportouttimer++;
                completepercent.clear();
                lowest = 0;
                try {
                    Thread.sleep(SLEEP);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }
    public double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();

        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
    private class selectingarea implements Runnable {
        @Override
        public void run() {
            BotUtils.sysMsg("Drag area over smelters/Ovens", Color.WHITE);
            PBotAPI.selectArea();
            //gui.map.PBotAPISelect = true;
            // while(gui.map.PBotAPISelect)
            //BotUtils.sleep(100);
            // BotUtils.sysMsg("Adding", Color.WHITE);
            try {
                selectedAreaA = PBotAPI.getSelectedAreaA();
                selectedAreaB = PBotAPI.getSelectedAreaB();
                crucibles.addAll(Crucibles());
                stockpiles.addAll(Stockpiles());
                lbls.settext(stockpiles.size() + "");
                lblc.settext(crucibles.size() + "");
            }catch(NullPointerException q){BotUtils.sysMsg("Error detected, please reopen the bot and try again.",Color.white);}
        }
    }

    private void getfuel() {
        GameUI gui = gameui();
        Glob glob = gui.map.glob;
        for (Gob s : stockpiles) {
            if (terminate)
                return;

            // make sure stockpile still exists
            synchronized (glob.oc) {
                if (glob.oc.getgob(s.id) == null)
                    continue;
            }

            // navigate to the stockpile and load up on fuel
            gameui().map.pfRightClick(s, -1, 3, 1, null);
            try {
                gui.map.pfthread.join();
            } catch (InterruptedException e) {
                return;
            }

            // return if got enough fuel
            int availableFuelCoal = gui.maininv.getItemPartialCount("Coal");
            int availableFuelBlock = gui.maininv.getItemPartialCount("Block");
            int availableFuelBranch = gui.maininv.getItemPartialCount("Branch");
            if (availableFuelCoal >= 9 || availableFuelBlock >= 3 || availableFuelBranch >= 18)
                return;
        }
    }

    public void gobselect(Gob gob) {
        Resource res = gob.getres();
        if (res != null) {
            if (res.name.equals("gfx/terobjs/steelcrucible")) {
                if (!crucibles.contains(gob)) {
                    crucibles.add(gob);
                    lblc.settext(crucibles.size() + "");
                }
            } else if (res.name.equals("gfx/terobjs/stockpile-coal") || res.name.equals("gfx/terobjs/stockpile-wblock") || res.name.equals("gfx/terobjs/stockpile-branch")) {
                if (!stockpiles.contains(gob)) {
                    stockpiles.add(gob);
                    lbls.settext(stockpiles.size() + "");
                }
            }
        }
    }

    @Override
    public void wdgmsg(Widget sender, String msg, Object... args) {
        if (sender == cbtn)
            reqdestroy();
        else
            super.wdgmsg(sender, msg, args);
    }

    @Override
    public boolean type(char key, KeyEvent ev) {
        if (key == 27) {
            if (cbtn.visible)
                reqdestroy();
            return true;
        }
        return super.type(key, ev);
    }

    public void terminate() {
        terminate = true;
        if (runner != null)
            runner.interrupt();
        this.destroy();
    }
    public ArrayList<Gob> Crucibles() {
        // Initialises list of crops to harvest between the selected coordinates
        ArrayList<Gob> gobs = new ArrayList<Gob>();
        double bigX = selectedAreaA.x > selectedAreaB.x ? selectedAreaA.x : selectedAreaB.x;
        double smallX = selectedAreaA.x < selectedAreaB.x ? selectedAreaA.x : selectedAreaB.x;
        double bigY = selectedAreaA.y > selectedAreaB.y ? selectedAreaA.y : selectedAreaB.y;
        double smallY = selectedAreaA.y < selectedAreaB.y ? selectedAreaA.y : selectedAreaB.y;
        synchronized (ui.sess.glob.oc) {
            for (Gob gob : ui.sess.glob.oc) {
                if (gob.rc.x <= bigX && gob.rc.x >= smallX && gob.getres() != null && gob.rc.y <= bigY
                        && gob.rc.y >= smallY && gob.getres().name.contains("steel")) {
                    gobs.add(gob);
                }
            }
        }
        gobs.sort(new CoordSort());
        return gobs;
    }
    public ArrayList<Gob> Stockpiles() {
        // Initialises list of crops to harvest between the selected coordinates
        ArrayList<Gob> gobs = new ArrayList<Gob>();
        double bigX = selectedAreaA.x > selectedAreaB.x ? selectedAreaA.x : selectedAreaB.x;
        double smallX = selectedAreaA.x < selectedAreaB.x ? selectedAreaA.x : selectedAreaB.x;
        double bigY = selectedAreaA.y > selectedAreaB.y ? selectedAreaA.y : selectedAreaB.y;
        double smallY = selectedAreaA.y < selectedAreaB.y ? selectedAreaA.y : selectedAreaB.y;
        synchronized (ui.sess.glob.oc) {
            for (Gob gob : ui.sess.glob.oc) {
                if (gob.rc.x <= bigX && gob.rc.x >= smallX && gob.getres() != null && gob.rc.y <= bigY
                        && gob.rc.y >= smallY && gob.getres().name.contains("branch")) {
                    gobs.add(gob);
                }
            }
        }
        gobs.sort(new CoordSort());
        return gobs;
    }
    class CoordSort implements Comparator<Gob> {
        public int compare(Gob a, Gob b) {
            if (a.rc.floor().x == b.rc.floor().x) {
                if (a.rc.floor().x % 2 == 0)
                    return (a.rc.floor().y < b.rc.floor().y) ? 1 : (a.rc.floor().y > b.rc.floor().y) ? -1 : 0;
                else
                    return (a.rc.floor().y < b.rc.floor().y) ? -1 : (a.rc.floor().y > b.rc.floor().y) ? 1 : 0;
            } else
                return (a.rc.floor().x < b.rc.floor().x) ? -1 : (a.rc.floor().x > b.rc.floor().x) ? 1 : 0;
        }
    }
}