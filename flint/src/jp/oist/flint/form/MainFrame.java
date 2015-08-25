/* -*- Mode: Java; tab-width: 4; indent-tabs-mode: nil; c-basic-offset: 4 -*- vim:set ts=4 sw=4 sts=4 et: */
package jp.oist.flint.form;

import jp.oist.flint.backend.ModelLoader;
import jp.oist.flint.command.Session;
import jp.oist.flint.control.FileChooser;
import jp.oist.flint.control.ModelFileTransferHandler;
import jp.oist.flint.dao.SimulationDao;
import jp.oist.flint.dao.TaskDao;
import jp.oist.flint.desktop.Desktop;
import jp.oist.flint.desktop.Document;
import jp.oist.flint.desktop.IDesktopListener;
import jp.oist.flint.executor.PhspProgressMonitor;
import jp.oist.flint.executor.PhspSimulator;
import jp.oist.flint.executor.SimulatorService;
import jp.oist.flint.form.job.IProgressManager;
import jp.oist.flint.form.sub.SubFrame;
import jp.oist.flint.job.Progress;
import jp.oist.flint.k3.K3Client;
import jp.oist.flint.k3.K3Request;
import jp.oist.flint.k3.K3RequestBuilder;
import jp.oist.flint.phsp.PhspException;
import jp.oist.flint.phsp.PhspReader;
import jp.oist.flint.phsp.PhspReaderListener;
import jp.oist.flint.phsp.PhspWriter;
import jp.oist.flint.rpc.ICallee;
import jp.oist.flint.sedml.ISimulationConfiguration;
import jp.oist.flint.sedml.ISimulationConfigurationList;
import jp.oist.flint.sedml.SedmlException;
import jp.oist.flint.sedml.SedmlReader;
import jp.oist.flint.sedml.SedmlWriter;
import jp.oist.flint.util.Utility;
import com.google.protobuf.ByteString;
import org.apache.log4j.Logger;
import org.xml.sax.SAXException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.HeadlessException;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyVetoException;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.WindowConstants;
import javax.swing.event.EventListenerList;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

/**
 * This is the class of the main window.
 */
public class MainFrame extends javax.swing.JFrame 
    implements ICallee, IDesktopListener,
               IMenuDelegator, ISimulationConfigurationList, IFrame {

    static {
        try {
            jp.oist.flint.plotter.PlotterLoader.register("gnuplot", jp.oist.flint.gnuplot.Plotter.class);
        } catch (BackingStoreException bse) {
            Logger.getRootLogger().error(bse.getMessage());
        }
    }

    public final static int WIDTH = 800;

    public final static int HEIGHT = 600;

    public final static int MIN_WIDTH = 800;

    public final static int MIN_HEIGHT = 600;

    private final Desktop mDesktop;

    private final Session mSession;

    private final EventListenerList mEventListenerList;

    private File mPhspFile = null;

    private PhspSimulator mSimulator = null;

    private ProgressPane mProgressPane;

    private ControlPane mControlPane;

    public MainFrame(Desktop desktop, Session session)
        throws IOException {
        super();
        mDesktop = desktop;
        mSession = session;
        mEventListenerList = new EventListenerList();
        URL iconUrl = getClass().getResource("/jp/oist/flint/image/icon.png");
        setIconImage(new ImageIcon(iconUrl).getImage());
        setTransferHandler(new ModelFileTransferHandler(this));

        initComponents();
        loadRecentModels();
    }

    private void initComponents () {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Flint");

        setMinimumSize(new Dimension(MIN_WIDTH, MIN_HEIGHT));
        setMaximumSize(new Dimension(
            Short.MAX_VALUE, Short.MAX_VALUE
        ));
        setSize(new Dimension (WIDTH, HEIGHT));
        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setLocationRelativeTo(null);

        MenuBar menuBar = MenuBar.getInstance();
        menuBar.setDelegator(this);
        setJMenuBar(menuBar);
        mDesktop.addListener(menuBar);

        setContentPane(createContentPane());
        pack();
    }

    private JComponent createContentPane () {
        final JSplitPane contentPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        contentPane.setDividerSize(10);
        contentPane.setDividerLocation(638);
        contentPane.setOneTouchExpandable(true);

        final JPanel peripheralPane = new JPanel(new BorderLayout());
        peripheralPane.setEnabled(false);

        peripheralPane.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
        peripheralPane.setMinimumSize(new Dimension(150, Short.MAX_VALUE));

        contentPane.setLeftComponent(mDesktop.getPane());
        contentPane.setRightComponent(peripheralPane);

        mProgressPane = ProgressPane.getInstance();
        mProgressPane.setMaximumSize(new Dimension(Short.MAX_VALUE, Short.MAX_VALUE));
        mProgressPane.setMinimumSize(new Dimension(0, 0));
        mProgressPane.setPreferredSize(new Dimension(150, 510));
        mDesktop.addListener(mProgressPane);

        mControlPane = ControlPane.getInstance();
        mControlPane.setMaximumSize(new Dimension(Short.MAX_VALUE, 60));
        mControlPane.setMinimumSize(new Dimension(0, 60));
        mControlPane.setPreferredSize(new Dimension(150, 60));

        peripheralPane.add(mProgressPane, BorderLayout.CENTER);
        peripheralPane.add(mControlPane, BorderLayout.SOUTH);

        return contentPane;
    }

    private void loadRecentModels () {
        MenuBar.getInstance()
            .fireUpdateRecentModels(mSession.getRecentModels());
    }

    private void simulationRun () {
        try {
            for (SubFrame subFrame : getSubFrames())
                subFrame.reloadJobViewer();

            // TOOD
            for (ProgressCell cell : mProgressPane.getListCells())
                cell.progressStarted();

            SimulatorService service = new SimulatorService(this);

            final PhspSimulator simulator = new PhspSimulator(service, this, mDesktop);
            final PhspProgressMonitor monitor = new PhspProgressMonitor(simulator);
            simulator.addSimulationListener(new PhspSimulator.Listener() {
                @Override
                public void onSimulationStarted(PhspSimulator.Event evt) { 
                    mProgressPane.repaint();
                }

                @Override
                public void onSimulationExited(PhspSimulator.Event evt) {
                    mProgressPane.repaint();
                    PhspSimulator simulator = (PhspSimulator)evt.getSource();
                    try {
                        monitor.stop();
                        //TODO 
                        ProgressPane progressView;
                        Boolean result = simulator.get();
                        if (result) {
                            JOptionPane.showMessageDialog(MainFrame.this,
                                    "Simulation completed", "Simulation completed",
                                    JOptionPane.PLAIN_MESSAGE);
                        } else {

                        }
                    } catch (InterruptedException | ExecutionException | HeadlessException ex) {
                        File logFile = simulator.getLogFile();
                        StringBuilder sb = new StringBuilder();
                        if (logFile != null) {
                            try (FileInputStream fis = new FileInputStream(logFile);
                                 InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                                 BufferedReader reader = new BufferedReader(isr)) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    sb.append(line).append(System.getProperty("line.separator"));
                                }
                            } catch (IOException ex1) {
                                Logger.getRootLogger().error(ex1.getMessage());
                            }
                        }
                        String detail = sb.toString();
                        MessageDialog.showMessageDialog(MainFrame.this, 
                                "The following error occurred during simulation:",
                                detail, 
                                "Error on simulation",
                                JOptionPane.ERROR_MESSAGE, null, new Object[]{" OK "});
                    }
                }
            });
            for (SubFrame subFrame : getSubFrames())
                simulator.addSimulationListener(subFrame);
            simulator.addSimulationListener(MenuBar.getInstance());
            simulator.addSimulationListener(mControlPane);

            monitor.addPropertyChangeListener(new PropertyChangeListener(){
                @Override
                public void propertyChange(PropertyChangeEvent e) {
                    String propertyName = e.getPropertyName();
                    if ("progress".equals(propertyName)) {
                        if (e instanceof PhspProgressMonitor.Event) {
                            final PhspProgressMonitor.Event evt = (PhspProgressMonitor.Event)e;
                            SwingUtilities.invokeLater(new Runnable() {
                                @Override
                                public void run() {
                                    String modelPath = (String)evt.getClientProperty("modelPath");
                                    SubFrame subFrame = findSubFrame(modelPath);

                                    SimulationDao simulationDao = simulator.getSimulationDao();
                                    TaskDao taskDao = simulationDao.obtainTask(new File(subFrame.getRelativeModelPath()));

                                    Progress progress = (Progress)evt.getNewValue();
                                    Map<String, Number> target = (Map<String, Number>)evt.getClientProperty("target");
                                    IProgressManager progressMgr = subFrame.getProgressManager();
                                    int index = progressMgr.indexOf(target);

                                    progressMgr.setProgress(index, progress);

                                    if (taskDao.isCancelled())
                                        progressMgr.setCancelled(index, rootPaneCheckingEnabled);

                                    int taskProgress = taskDao.getProgress();
                                    ProgressCell cell = 
                                            mProgressPane.getListCellOfModel(new File(modelPath));

                                    String status;
                                    if (taskDao.isFinished()) {
                                        status = (taskDao.isCancelled())? "finished" : "completed";
                                        cell.progressFinished(status, 0, 100, taskProgress);
                                    } else if (taskDao.isStarted()) { 
                                        status = (taskDao.isCancelled())? "cancelling..." : taskProgress + " %";
                                        cell.setProgress(status, 0, 100, taskProgress);
                                    }
                                }
                            });
                        }
                    }
                }
            });

            simulator.execute();
            mSimulator = simulator;
            monitor.start();
        } catch (IOException | ParserConfigurationException | PhspException |
                 SQLException | SedmlException | TransformerException ex) {
            showErrorDialog(ex.getMessage(), "ERROR");
        }
    }

    public boolean openPhsp(final File phspFile) {
        List<SubFrame> subFrames = getSubFrames();
        int numberOfSubView = subFrames.size();
        if (numberOfSubView > 0) {
            int ans = JOptionPane.showConfirmDialog(this,
                            "Is it ok to close editing files?",
                            "Close a file",
                            JOptionPane.YES_NO_OPTION);

            if (ans != JOptionPane.YES_OPTION) return false;
            closeAll();
        }

        mPhspFile = phspFile;
        try {
            setEditable(false);
            ModelLoaderLogger logger = new ModelLoaderLogger(mDesktop);
            PhspReader phspLoader = new PhspReader(phspFile);
            phspLoader.addPropertyChangeListener(new PhspReaderListener(logger));
            phspLoader.addPropertyChangeListener(new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    String propertyName = evt.getPropertyName();
                    Object newValue = evt.getNewValue();
                    if ("state".equals(propertyName) 
                            && SwingWorker.StateValue.DONE.equals(newValue)) {
                        mSession.updateRecentModels(phspFile);
                        loadRecentModels();
                        setEditable(true);
                    }
                }
            });
            phspLoader.execute();
            return true;
        } catch (IOException ex) {
            setEditable(true);
            showErrorDialog(ex.getMessage(), "Error on load file of phsp");
            return false;
        }
    }

    public boolean closeModel (SubFrame subFrame) {
        if (mSimulator != null && mSimulator.isStarted()) {
            JOptionPane.showMessageDialog(this, 
                    "Could not close the model. \n"
                        + "It's running the simulation yet.", 
                    "Error on close the model", 
                    JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        if (subFrame == null) 
            return false;

        if (!subFrame.isClosed())
            mDesktop.removeDocument(subFrame.getDocument());
        return true;
    }

    public void closeAll () {
        for (SubFrame subFrame : getSubFrames())
            closeModel(subFrame);
    }

    public SubFrame getSelectedSubFrame () {
        return (SubFrame) mDesktop.getPane().getSelectedFrame();
    }

    public List<SubFrame> getSubFrames() {
        return mDesktop.getSubFrames();
    }

    public void setProgress(Object key, int progress) {
        if (key instanceof String) {
            String modelPath = (String)key;
            ProgressCell cell = mProgressPane.getListCellOfModel(new File(modelPath));
            String msg = progress + " %";
            cell.setProgress(msg, 0, 100, progress);
            return;
        }
        throw new IllegalArgumentException("key must be set model file path.");
    }

    public void setEditable (boolean editable) {
        List<SubFrame> subFrames = getSubFrames();
        for (SubFrame subFrame : subFrames)
            subFrame.setEditable(editable);
        mControlPane.setSimulationRunEnabled(editable);
    }

    /*
     * Implements MenuBar Delegater
     */
    @Override
    public void openPerformed (Object source) { 
        FileChooser fc = new FileChooser(this, "Open model", FileChooser.Mode.LOAD, mSession.getLastPath());
        JFileChooser jfc = fc.getJFileChooser();
        if (jfc != null) {
            jfc.setAcceptAllFileFilterUsed(false);
            
            FileNameExtensionFilter xmlFilter = new FileNameExtensionFilter("XML files (*.xml)", "xml"); 
            FileNameExtensionFilter modelFilter = new FileNameExtensionFilter("Model files (*.isml, *.phml, *.phz, *.sbml)", "isml", "phml", "phz", "sbml");
            FileNameExtensionFilter phspFilter = new FileNameExtensionFilter("PHSP files (*.phsp)", "phsp");
            jfc.addChoosableFileFilter(modelFilter);
            jfc.addChoosableFileFilter(phspFilter);
            jfc.addChoosableFileFilter(xmlFilter);
            jfc.addChoosableFileFilter(jfc.getAcceptAllFileFilter());
            jfc.setFileFilter(modelFilter);
        }

        if (!fc.showDialog()) return;

        openModel(fc.getSelectedFile());
    }

    @Override
    public void recentModelPerformed (Object source, File f) {
        openModel(f);
    }

    @Override
    public void closePerformed (Object source) {
        closeModel(getSelectedSubFrame());
    }

    @Override
    public void loadConfigurationPerformed (Object source) {
        Preferences prefs = Preferences.userRoot().node("/jp/oist/flint");
        String defaultPath = prefs.get("defaultConfigurationPath", "");

        FileChooser fc = new FileChooser(this, "Open SED-ML file", FileChooser.Mode.LOAD, defaultPath);
        JFileChooser jfc = fc.getJFileChooser();
        if (jfc != null) {
            jfc.setAcceptAllFileFilterUsed(false);

            FileNameExtensionFilter sedmlFilter = new FileNameExtensionFilter("SED-ML files (*.sedml, *.xml)", "sedml", "xml");

            jfc.addChoosableFileFilter(sedmlFilter);
            jfc.addChoosableFileFilter(jfc.getAcceptAllFileFilter());
            jfc.setFileFilter(sedmlFilter);
        }

        if (!fc.showDialog()) return;
        File file = fc.getSelectedFile();
        SedmlReader reader = new SedmlReader(file);
        try {
            if (reader.parse()) {
                prefs.put("defaultConfigurationPath", file.getParent());
                ISimulationConfigurationList configs = (ISimulationConfigurationList)reader.getHandler();

                for (SubFrame subFrame : getSubFrames()) {
                    ISimulationConfiguration config = 
                            configs.getConfigurationByModelPath(subFrame.getRelativeModelPath());
                    subFrame.load(config);
                }
            }
        } catch (IOException | ParserConfigurationException | SAXException e) {
            showErrorDialog(e.getMessage(), "Error on load configuration");
        }
    }

    @Override
    public void saveConfigurationPerformed (Object source) {
        FileChooser fc = new FileChooser(this, "Select SED-ML file", FileChooser.Mode.SAVE, "");
        if (fc.showDialog()) {
            final File file = fc.getSelectedFile();
            if (file.exists()) {
                int ans = JOptionPane.showConfirmDialog(this,
                                                        "Is it OK to replace the existing file?",
                                                        "Replace the existing file?",
                                                        JOptionPane.YES_NO_OPTION);
                if (ans != JOptionPane.YES_OPTION) return;
            }
            try (FileOutputStream fos = new FileOutputStream(file)) {
                SedmlWriter writer = new SedmlWriter(true);
                writer.writeSimulationConfiguration(this, fos);
            } catch (HeadlessException e) {
                showErrorDialog(e.getMessage(), "Error on saving configuration");
                return;
            } catch (IOException | ArithmeticException | SedmlException e) {
                showErrorDialog(e.getMessage(), "Error on saving configuration");
                return;
            }
            JOptionPane.showMessageDialog(this, "Saved configuration as " + file.getPath());
        }
    }

    @Override
    public void saveAsPhspPerformed (Object source) {
        try {
            if (mDesktop.isEmpty()) {
                showErrorDialog("Please open the phml/sbml model.", 
                                  "Error on saving phsp");
                return;
            }

            FileChooser fc = new FileChooser(this, "Select PHSP file", FileChooser.Mode.SAVE, "");
            JFileChooser jfc = fc.getJFileChooser();
            FileNameExtensionFilter xmlFilter = new FileNameExtensionFilter("XML files (*.xml)", "xml");
            FileNameExtensionFilter modelFilter = new FileNameExtensionFilter("PHSP files (*.phsp)", "phsp");
            jfc.addChoosableFileFilter(modelFilter);
            jfc.addChoosableFileFilter(xmlFilter);
            jfc.addChoosableFileFilter(jfc.getAcceptAllFileFilter());
            jfc.setFileFilter(modelFilter);
            File initFile = (mPhspFile == null)? new File("Untitled.phsp"): mPhspFile;
            jfc.setSelectedFile(initFile);

           if (fc.showDialog()) {
                File file = jfc.getSelectedFile();
                if (!file.getName().endsWith(".phsp"))
                    file = new File(file.getPath()+".phsp");

                if (file.exists()) {
                    int ans = JOptionPane.showConfirmDialog(this,
                                                            "Is it OK to replace the existing file?",
                                                            "Replace the existing file?",
                                                            JOptionPane.YES_NO_OPTION);
                    if (ans != JOptionPane.YES_OPTION) return;
                }

                setEnabled(false);

                try (FileOutputStream fos = new FileOutputStream(file)) {
                PhspWriter writer = new PhspWriter();
                writer.write(mDesktop, fos, false);
                }
                JOptionPane.showMessageDialog(this, "Saved phsp as " + file.getPath());
           }
        } catch (IOException | ParserConfigurationException | PhspException | TransformerException ex) {
            showErrorDialog(ex.getMessage(), "Saving as PHSP failed");
        } finally {
            setEnabled(true);
        }
    }

    @Override
    public void exitPerformed (Object source) {
        if (mSimulator != null)
            mSimulator.cancel(true);
        System.exit(0);
    }

    @Override
    public void copyPerformed (Object source) {
        SubFrame subFrame = getSelectedSubFrame();
        subFrame.copy();
    }

    @Override
    public void cutPerformed (Object source) {
        SubFrame subFrame = getSelectedSubFrame();
        subFrame.cut();
    }

    @Override
    public void preferencePerformed (Object source) {
       PreferenceDialog ad = new PreferenceDialog(this, true);
        ad.setVisible(true);
    }

    @Override
    public void simulationRunPerformed(Object source) {
        simulationRun();
    }

    @Override
    public void sendToK3Performed(Object source) {
        Preferences prefs = Preferences.userRoot().node("/jp/oist/flint/session/k3");
        final String encryptedUserId = prefs.get("encryptedUserId", null); 
        final String encryptedPassword = prefs.get("encryptedPassword", null); 

        if (encryptedUserId == null  || encryptedUserId.isEmpty() 
                || encryptedPassword == null || encryptedPassword.isEmpty()) {
            StringBuilder sb = new StringBuilder("Please specify your account of Flint K3.");
            sb.append(System.getProperty("line.separator"));
            sb.append("(Edit -> Preference -> K3)");
            showErrorDialog(sb.toString(), "Error on preference");
            return;
        }

        SubFrame subFrame  = getSelectedSubFrame();
        final Object retval = JOptionPane.showInputDialog(this,
                                                          "New job's title:",
                                                          "New job's title",
                 JOptionPane.QUESTION_MESSAGE, null, null,
                 subFrame.getModelFile().getName());

        if (retval == null)
            return;

        SwingWorker<Integer, Void> worker = new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() throws Exception {
                SubFrame subFrame  = getSelectedSubFrame();
                String jobName = (String)retval;
                String userId = Utility.decrypt(encryptedUserId);
                String passwd = Utility.decrypt(encryptedPassword);

                K3RequestBuilder reqBuilder = new K3RequestBuilder(
                        subFrame.getModelFile(), subFrame);
                K3Request request = reqBuilder.build(jobName, userId, passwd);

                K3Client k3 = new K3Client();
                return k3.submit(request);
            }
            @Override
            protected void done () {
                try {
                    int jobId = get();
                    String message = String.format("Submitted successfully your job to Flint K3 (Job ID : %d)", jobId);
                    String title = "Job submitted to Flint K3";
                    JOptionPane.showMessageDialog(MainFrame.this,
                                                  message,
                                                  title,
                                                  JOptionPane.INFORMATION_MESSAGE);
                } catch (InterruptedException | ExecutionException ex) {
                    showErrorDialog(ex.getMessage(),
                                    "Error on comminicating with Flint K3");
                }
            }
        };
        worker.execute();
    }

    @Override
    public void aboutPerformed (Object source) {
        AboutDialog ad = new AboutDialog(this);
        ad.setVisible(true);
    }

    @Override
    public void updateRecentModels(Object source, List<File>modelFiles) {
        JMenu menuRecentModels = (JMenu)source;
        menuRecentModels.removeAll();
        MenuBar menuBar = MenuBar.getInstance();
        for (final File model : modelFiles) {
            menuBar.addRecentModel(model);
        }
    }

    /*
     * Implements IFrame 
     */
    @Override
    public void showErrorDialog(String message, String title) {
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void showErrorDialog(ByteString message, String title) {
        JOptionPane.showMessageDialog(this, message.toStringUtf8(), title, JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void appendLog(String s) {
        Date date = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String d = sdf.format(date);

        Logger.getRootLogger().error("[" + d + "] " +  s  + System.getProperty("line.separator"));
    }

    public void notifyK3Enabled() {
        List<SubFrame> subFrames = getSubFrames();
        for (SubFrame subFrame : subFrames) {
            subFrame.notifyK3Enabled();
        }
    }

    public boolean openModel (final File file) {
        if (file == null) {
            // just ignore it
            return false;
        }
        if (!file.exists()) {
            String separator = System.getProperty("line.separator");
            String msg = String.format("The file named \"%s\" " + separator
                    + "does not exist.", file.getPath());
            showErrorDialog(msg, "Error on opening model");
            return false;
        }

        // check xml format
        String format = Utility.detectXMLFormat(file);

        if ("phsp".equals(format)) 
            return openPhsp(file);

        // check if the file is opened.
        for (SubFrame child : getSubFrames()) {
           if (child.getModelFile().getPath().equals(file.getPath())) {
               try {
                   child.setSelected(true);
               } catch (PropertyVetoException ex) {
                    // ignored
               }
               return true;
            }
        }

        String path;
        try {
            path = file.getCanonicalPath();
        } catch (IOException ex) {
            showErrorDialog("could not get canonical path : " + file.toString(),
                    "Error on opening model");
            return false;
        }

        if (!file.isFile()) {
            showErrorDialog("could not get canonical path : " + file.toString(),
                    "Error on opening model"); return false; }

        int len = (int)file.length();
        if (len == 0) {
            showErrorDialog("file has length 0 : " + path,
                    "Error on opening model");
            return false;
        }

        ModelLoaderLogger logger = new ModelLoaderLogger(mDesktop);
        setEditable(false);
        ModelLoader loader = new ModelLoader(file);
        loader.addPropertyChangeListener(new ModelFileLoaderListener(logger, file , loader));
        loader.addPropertyChangeListener(new ModelLoaderProgressDialog(this, path));
        loader.addPropertyChangeListener(new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                String propertyName = evt.getPropertyName();
                Object newValue = evt.getNewValue();
                if ("state".equals(propertyName) 
                    && SwingWorker.StateValue.DONE.equals(newValue)) {
                    mSession.updateRecentModels(file);
                    loadRecentModels();
                    setEditable(true);
                }
            }
        });
        loader.execute();
        return true;
    }

    @Override
    public void openModel(final String name) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                Path path = Paths.get(name);
                openModel(path.toFile());
            }
        });
    }

    public void setPlotterSettingTabEnabled(String defaultPlotter) {
        SubFrame subFrame = this.getSelectedSubFrame();
        if (subFrame != null) {
            subFrame.setPlotterSettingTabEnabled("gnuplot".equals(defaultPlotter));
        }
    }

    /*
     * implements ISimulationConfigurationList 
     */
    @Override
    public ISimulationConfiguration getConfiguration(int index) {
        return getSubFrames().get(index);
    }

    @Override
    public ISimulationConfiguration getConfigurationByModelPath(String modelPath) {
        return findSubFrame(modelPath);
    }

    public SubFrame findSubFrame (String modelPath) {
        for (SubFrame subFrame : getSubFrames()) {
            if (modelPath.equals(subFrame.getRelativeModelPath()))
                return subFrame;
        }
        return null;
    }

    @Override
    public List<ISimulationConfiguration> toList() {
        List<ISimulationConfiguration> configs = new ArrayList<>();
        for (SubFrame subFrame : getSubFrames())
            configs.add(subFrame);

        return configs;
    }

    @Override
    public int getConfigurationCount () {
        return mDesktop.getSize();
    }

    /* IDesktopListener */

    @Override
    public void documentAdded(Document doc) {
        Preferences prefs = Preferences.userRoot().node("/jp/oist/flint");
        String defaultPlotter = prefs.get("defaultPlotter", "");
        setPlotterSettingTabEnabled(defaultPlotter);
    }

    @Override
    public void documentRemoved(Document doc, boolean empty) {
        if (empty) {
            setEditable(false);
            requestFocus();
        }
    }
}
