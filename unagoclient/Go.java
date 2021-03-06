package unagoclient;

/*
 This file contains the Go applet and its main method.
 */

import unagoclient.board.Board;
import unagoclient.board.GoFrame;
import unagoclient.board.WoodPaint;
import unagoclient.dialogs.GetParameter;
import unagoclient.dialogs.Message;
import unagoclient.gui.*;
import unagoclient.igs.Connect;
import unagoclient.igs.ConnectionFrame;
import unagoclient.igs.connection.Connection;
import unagoclient.igs.connection.EditConnection;
import unagoclient.partner.ConnectPartner;
import unagoclient.partner.OpenPartnerFrame;
import unagoclient.partner.PartnerFrame;
import unagoclient.partner.partner.EditPartner;
import unagoclient.partner.partner.Partner;
import unagoclient.sound.UnaGoSound;
import rene.util.list.ListClass;
import rene.util.list.ListElement;

import javax.swing.*;
import java.applet.Applet;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.util.Locale;

/**
 * To get the password, when there is none (but a user name) and a server
 * connection is requested. Some users do not like their password to be
 * permanently written to the server.cfg file.
 */

class GetPassword extends GetParameter {
    /**
     *
     */
    private static final long serialVersionUID = 1L;
    Connection C;
    Go G;
    public String Password;

    public GetPassword(Frame f, Go g, Connection c) {
        super(f, Global.resourceString("Enter_Password_"), Global
                .resourceString("Password"), g, '*', true);
        this.C = c;
        this.G = g;
        this.setVisible(true);
    }

    @Override
    public boolean tell(Object o, String s) {
        this.Password = s;
        return true;
    }
}

/**
 * The Go applet is an applet, which resides in a frame opened from the main()
 * method of Go (or generated by the WWW applet that starts Go). This frame is
 * of class MainFrame.
 * <p>
 * It contains a card panel with two panels: the server connections and the
 * partner connections.
 * <p>
 * This applet handles all the buttons in the server and the partner panels.
 * <p>
 * Several private dialogs are used to edit the connection parameters, get the
 * password (if there is none in server.cfg, but automatic login is requested),
 * ask for closing the UnaGo application altogether and read in other parameters.
 *
 * @see MainFrame
 */

public class Go extends Applet implements DoActionListener, ActionListener {
    /**
     *
     */
    private static final long serialVersionUID = 1L;

    /**
     * This is the main method for the UnaGoClient application. It is normally
     * envoced via "java Go".
     * <p>
     * It opens a frame of class MainFrame, initializes the parameter list and
     * starts a dump on request in the command line. If a game name is entered
     * in the command line, it will also open a GoFrame displaying the game.
     * <p>
     * An important point is that the application will write its setup to
     * go.cfg.
     * <p>
     * Available arguments are
     * <ul>
     * <li>-h sets the home directory for the application
     * <li>-d starts a session dump to dump.dat
     * <li>-t dumps tp the termimal too
     * <li>another argument opens a local SGF file immediately
     * </ul>
     */
    public static void main(String args[]) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (final Exception e) {
        }

        // scan arguments
        int na = 0;
        boolean homefound = false;
        String localgame = "";
        while (args.length > na) {
            if (args.length - na >= 2 && args[na].startsWith("-l")) {
                Locale.setDefault(new Locale(args[na + 1], ""));
                na += 2;
            } else if (args.length - na >= 2 && args[na].startsWith("-h")) {
                Global.home(args[na + 1]);
                na += 2;
                homefound = true;
            } else if (args[na].startsWith("-d")) {
                Dump.open("dump.dat");
                na++;
            } else if (args[na].startsWith("-t")) {
                Dump.terminal(true);
                na++;
            } else {
                localgame = args[na];
                na++;
            }
        }
        // initialize some Global things
        Global.setApplet(false);
        if (!homefound) {
            Global.home(System.getProperty("user.home"));
        }
        Global.version51();
        Global.readparameter(".go.cfg"); // read setup
        Global.createfonts();
        CloseFrame CF;
        Global.frame(CF = new CloseFrame("Global")); // a default invisible
        // frame
        CF.seticon("iunago.gif");
        // create a MainFrame
        Go.F = new MainFrame(Global.resourceString("_UnaGo_"));
        // add a go applet to it and initialize it
        Go.F.add("Center", Go.go = new Go());
        Go.go.init();
        Go.go.start();
        Go.F.setVisible(true);
        Global.loadmessagefilter(); // load message filters, if available
        UnaGoSound.play("high", "", true); // play a welcome sound
        if (!localgame.equals("")) {
            Go.openlocal(localgame);
        } else if (rene.gui.Global.getParameter("beauty", false))
        // start a board painter with the last known
        // board dimensions
        {
            Board.woodpaint = new WoodPaint(Go.F);
        }
    }

    /**
     * open a local game window (called from main)
     */
    static void openlocal(String file) {
        final GoFrame gf = new GoFrame(new Frame(), "Local");
        gf.load(file);
    }

    int Test = 0;
    java.awt.List L, PL;
    ListClass ConnectionList, PartnerList;
    JButton CConnect, CEdit, CAdd, CDelete, PConnect, PEdit, PAdd, PDelete,
            POpen;
    static Go go;
    String Server = "", MoveStyle = "", Encoding = "";

    int Port;

    public OpenPartnerFrame OPF = null;

    /**
     * The frame containing the Go applet
     */
    public static MainFrame F = null;

    public Go() {
        this.Server = "";
        this.Port = 0;
        this.MoveStyle = "";
        this.Encoding = "";
    }

    /**
     * Constructor for use with a specific server and port
     */
    public Go(String server, int port, String movestyle, String encoding) {
        this.Server = server;
        this.Port = port;
        this.MoveStyle = movestyle;
        this.Encoding = encoding;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == this.L) {
            this.doAction("ConnectServer");
        }
        if (e.getSource() == this.PL) {
            this.doAction("ConnectPartner");
        } else {
            this.doAction(e.getActionCommand());
        }
    }

    @Override
    public void doAction(String o) {
        if ("ConnectServer".equals(o)) {
            final String s = this.L.getSelectedItem();
            if (s == null) {
                return;
            }
            final Connection c = this.find(this.L.getSelectedItem());
            if (c != null) // try to connect, if not already tried
            {
                if (c.Trying) {
                    new Message(
                            Global.frame(),
                            Global.resourceString("Already_trying_this_connection"));
                    return;
                }
                if (c.Password.equals("") && !c.User.equals("")
                        && rene.gui.Global.getParameter("automatic", true))
                // get password, if there is a user name, but no password,
                // and automatic login
                {
                    final GetPassword GP = new GetPassword(Go.F, this, c);
                    if (GP.Password.equals("")) {
                        return;
                    }
                    // create a connection frame and connect via
                    // the connect class
                    final ConnectionFrame cf = new ConnectionFrame(
                            Global.resourceString("Connection_to_") + c.Name
                            + Global.resourceString("_as_") + c.User,
                            c.Encoding);
                    Global.setwindow(cf, "connection", 500, 400);
                    new Connect(c, GP.Password, cf);
                } else { // create a connection frame and connect via
                    // the connect class
                    final ConnectionFrame cf = new ConnectionFrame(
                            Global.resourceString("Connection_to_") + c.Name
                            + Global.resourceString("_as_") + c.User,
                            c.Encoding);
                    Global.setwindow(cf, "connection", 500, 400);
                    new Connect(c, cf);
                }
                return;
            }
        } else if ("ConnectPartner".equals(o)) {
            final String s = this.PL.getSelectedItem();
            if (s == null) {
                return;
            }
            final Partner c = this.pfind(this.PL.getSelectedItem());
            if (c != null) // try connecting to this partner server, if not
            // trying already
            {
                if (c.Trying) {
                    new Message(
                            Global.frame(),
                            Global.resourceString("Already_trying_this_connection"));
                    return;
                }
                // create a PartnerFrame and connect via ConnectPartner class
                final PartnerFrame cf = new PartnerFrame(
                        Global.resourceString("Connection_to_") + c.Name, false);
                Global.setwindow(cf, "partner", 500, 400);
                new ConnectPartner(c, cf);
            }
        } else if ("EditServer".equals(o)) {
            final String s = this.L.getSelectedItem();
            if (s == null) {
                return;
            }
            final Connection c = this.find(this.L.getSelectedItem());
            if (c != null) {
                new EditConnection(Go.F, this.ConnectionList, c, this);
            }
        } else if ("EditPartner".equals(o)) {
            final String s = this.PL.getSelectedItem();
            if (s == null) {
                return;
            }
            final Partner c = this.pfind(this.PL.getSelectedItem());
            if (c != null) {
                new EditPartner(Go.F, this.PartnerList, c, this);
            }
        } else if ("AddServer".equals(o)) {
            new EditConnection(Go.F, this.ConnectionList, this);
        } else if ("AddPartner".equals(o)) {
            new EditPartner(Go.F, this.PartnerList, this);
        } else if ("DeleteServer".equals(o) && this.L.getSelectedItem() != null) {
            ListElement lc = this.ConnectionList.first();
            Connection co;
            while (lc != null) {
                co = (Connection) lc.content();
                if (co.Name.equals(this.L.getSelectedItem())) {
                    this.ConnectionList.remove(lc);
                }
                lc = lc.next();
            }
            this.updatelist();
        } else if ("DeletePartner".equals(o)
                && this.PL.getSelectedItem() != null) {
            ListElement lc = this.PartnerList.first();
            Partner co;
            while (lc != null) {
                co = (Partner) lc.content();
                if (co.Name.equals(this.PL.getSelectedItem())) {
                    this.PartnerList.remove(lc);
                }
                lc = lc.next();
            }
            this.updateplist();
        } else if (Global.resourceString("Open_").equals(o)) {
            if (this.OPF == null) {
                this.OPF = new OpenPartnerFrame(this);
            } else {
                this.OPF.refresh();
            }
        }
    }

    /**
     * search a specific connection by name
     */
    public Connection find(String s) {
        ListElement lc = this.ConnectionList.first();
        Connection c;
        while (lc != null) {
            c = (Connection) lc.content();
            if (c.Name.equals(s)) {
                return c;
            }
            lc = lc.next();
        }
        return null;
    }

    /**
     * This init routines has two flavours. One is for specific servers (as used
     * when the applet is on a Web page of the go server) and one for general
     * servers.
     * <p>
     * The general setup will create a server and a partner panel. Those will be
     * put into a card panel using the CardJPanel class. The class completely
     * builds these two panels int this version.
     *
     * @see unagoclient.gui.CardPanel
     */

    @Override
    public void init() {
        this.setLayout(new BorderLayout());
        if (this.Server.equals("")) // general setup
        {
            // create a card panel
            final CardPanel cardp = new CardPanel();
            // Server connections panel
            final JPanel p1 = new MyPanel();
            p1.setLayout(new BorderLayout());
            // add north label
            p1.add("North",
                    new MyLabel(Global.resourceString("Server_Connections__")));
            // add button panel
            final JPanel p = new MyPanel();
            Global.setcomponent(p);
            p.add(this.CConnect = new ButtonAction(this, Global
                    .resourceString("Connect"), "ConnectServer"));
            p.add(new MyLabel(" "));
            p.add(this.CEdit = new ButtonAction(this, Global
                    .resourceString("Edit"), "EditServer"));
            p.add(this.CAdd = new ButtonAction(this, Global
                    .resourceString("Add"), "AddServer"));
            p.add(this.CDelete = new ButtonAction(this, Global
                    .resourceString("Delete"), "DeleteServer"));
            p1.add("South", p);
            // add center list with servers
            this.L = new java.awt.List();
            this.L.addActionListener(this);
            // L.setFont(Global.Monospaced);
            // L.setBackground(Global.gray);
            Connection c;
            String l;
            this.ConnectionList = new ListClass();
            try
            // read servers from server.cfg
            {
                final BufferedReader in = Global.getStream(".server.cfg");
                while (true) {
                    l = in.readLine();
                    if (l == null || l.equals("")) {
                        break;
                    }
                    c = new Connection(l);
                    if (c.valid()) {
                        this.L.add(c.Name);
                        this.ConnectionList.append(new ListElement(c));
                    } else {
                        break;
                    }
                }
                in.close();
            } catch (final Exception ex) {
            }
            if (this.L.getItemCount() > 0) {
                this.L.select(0);
            }
            p1.add("Center", this.L);
            // add the partner panel to the card panel
            cardp.add(p1, Global.resourceString("Server_Connections"));
            // partner connections panel
            final JPanel p2 = new MyPanel();
            p2.setLayout(new BorderLayout());
            // north label
            p2.add("North",
                    new MyLabel(Global.resourceString("Partner_Connections__")));
            // list class for partner connections
            this.PL = new java.awt.List();
            this.PL.addActionListener(this);
            // PL.setFont(Global.Monospaced);
            // PL.setBackground(Global.gray);
            Partner cp;
            this.PartnerList = new ListClass();
            try
            // read connections from partner.cfg
            {
                final BufferedReader in = Global.getStream(".partner.cfg");
                while (true) {
                    l = in.readLine();
                    if (l == null || l.equals("")) {
                        break;
                    }
                    cp = new Partner(l);
                    if (cp.valid()) {
                        this.PL.add(cp.Name);
                        this.PartnerList.append(new ListElement(cp));
                    } else {
                        break;
                    }
                }
                in.close();
            } catch (final Exception ex) {
            }
            if (this.PL.getItemCount() > 0) {
                this.PL.select(0);
            }
            Global.PartnerList = this.PartnerList;
            p2.add("Center", this.PL);
            // button panel
            final JPanel pp = new MyPanel();
            pp.add(this.PConnect = new ButtonAction(this, Global
                    .resourceString("Connect"), "ConnectPartner"));
            pp.add(new MyLabel(" "));
            pp.add(this.PEdit = new ButtonAction(this, Global
                    .resourceString("Edit"), "EditPartner"));
            pp.add(this.PAdd = new ButtonAction(this, Global
                    .resourceString("Add"), "AddPartner"));
            pp.add(this.PDelete = new ButtonAction(this, Global
                    .resourceString("Delete"), "DeletePartner"));
            pp.add(new MyLabel(" "));
            pp.add(this.POpen = new ButtonAction(this, Global
                    .resourceString("Open_")));
            p2.add("South", pp);
            cardp.add(p2, Global.resourceString("Partner_Connections"));
            // add the card panel to the applet
            this.add("Center", cardp);
        } else
        // specific server setup
        {
            // similar to the above, but simpler and using a single panel only
            final JPanel p1 = new MyPanel();
            p1.setLayout(new BorderLayout());
            p1.add("North",
                    new MyLabel(Global.resourceString("Server_Connection__")));
            final JPanel p = new MyPanel();
            Global.setcomponent(p);
            p.add(this.CConnect = new ButtonAction(this, Global
                    .resourceString("Connect"), "ConnectServer"));
            p1.add("South", p);
            this.L = new java.awt.List();
            this.L.setFont(Global.Monospaced);
            this.L.addActionListener(this);
            this.ConnectionList = new ListClass();
            this.ConnectionList.append(new ListElement(new Connection("["
                    + this.Server + "] [" + this.Server + "] [" + this.Port
                    + "] [" + "] [" + "] [" + this.MoveStyle + "] ["
                    + this.Encoding + "]")));
            this.L.add(this.Server);
            if (this.L.getItemCount() > 0) {
                this.L.select(0);
            }
            p1.add("Center", this.L);
            this.add("Center", p1);
        }
    }

    @Override
    public void itemAction(String o, boolean flag) {
    }

    /**
     * find a specific partner server by name
     */
    public Partner pfind(String s) {
        ListElement lc = this.PartnerList.first();
        Partner c;
        while (lc != null) {
            c = (Partner) lc.content();
            if (c.Name.equals(s)) {
                return c;
            }
            lc = lc.next();
        }
        return null;
    }

    /**
     * update the list of servers
     */
    public void updatelist() {
        if (Global.isApplet()) {
            return;
        }
        try {
            final PrintWriter out = new PrintWriter(new FileOutputStream(
                    Global.home() + ".server.cfg"));
            ListElement lc = this.ConnectionList.first();
            this.L.removeAll();
            while (lc != null) {
                final Connection c = (Connection) lc.content();
                this.L.add(c.Name);
                c.write(out);
                lc = lc.next();
            }
            out.close();
        } catch (final Exception e) {
            if (Go.F != null) {
                new Message(Go.F,
                        Global.resourceString("Could_not_write_to_server_cfg"));
            }
        }
    }

    /**
     * update the list of partners
     */
    public void updateplist() {
        if (Global.isApplet()) {
            return;
        }
        try {
            final PrintWriter out = new PrintWriter(new FileOutputStream(
                    Global.home() + ".partner.cfg"));
            ListElement lc = this.PartnerList.first();
            this.PL.removeAll();
            while (lc != null) {
                final Partner c = (Partner) lc.content();
                this.PL.add(c.Name);
                c.write(out);
                lc = lc.next();
            }
            out.close();
        } catch (final Exception e) {
            if (Go.F != null) {
                new Message(Go.F,
                        Global.resourceString("Could_not_write_to_partner_cfg"));
            }
        }
    }
}
