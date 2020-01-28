package nl.utwente.ewi.fmt.uppaalSMC.urpal.ui;

import com.google.inject.Injector;
import com.uppaal.engine.Problem;
import com.uppaal.model.core2.Document;
import com.uppaal.model.io2.XMLWriter;
import com.uppaal.model.system.UppaalSystem;
import com.uppaal.model.system.concrete.ConcreteTrace;
import com.uppaal.model.system.symbolic.SymbolicTrace;
import com.uppaal.plugin.Plugin;
import com.uppaal.plugin.PluginWorkspace;
import com.uppaal.plugin.Registry;
import com.uppaal.plugin.Repository;
import nl.utwente.ewi.fmt.uppaalSMC.NSTA;
import nl.utwente.ewi.fmt.uppaalSMC.parser.UppaalSMCStandaloneSetup;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheckResult;
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.*;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.json.simple.parser.JSONParser;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Element;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("serial")
public class MainUI extends JPanel implements Plugin, PluginWorkspace, PropertyChangeListener {
    private JTextArea specArea;
    private static XtextResourceSet rs;

    public static Repository<Document> getDocument() {return docr; }

    private static Repository<Document> docr;

    public static Repository<SymbolicTrace> getTracer() {
        return tracer;
    }

    private static Repository<SymbolicTrace> tracer;

    public static Repository<ConcreteTrace> getConcreteTracer() {
        return concreteTracer;
    }

    private static Repository<ConcreteTrace> concreteTracer;

    public static Repository<ArrayList<Problem>> getProblemr() {
        return problemr;
    }

    private static Repository<ArrayList<Problem>> problemr;

    public static Repository<UppaalSystem> getSystemr() {
        return systemr;
    }

    private static Repository<UppaalSystem> systemr;

    public static Repository<MainUI> getGuir() {
        return guir;
    }

    private static Repository<MainUI> guir;

    private static SanityLogRepository slr;

    public static SanityLogRepository getSlr() {
        return slr;
    }

    private JButton runButton;
    private JEditorPane resultPane;
    private boolean selected;
    private double zoom;

    private final PluginWorkspace[] workspaces = new PluginWorkspace[1];

    public static NSTA load(Document d) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            new XMLWriter(out).visitDocument(d);
            byte[] ba = out.toByteArray();
//            System.out.println("input: ");
//            System.out.println(new String(ba));
            InputStream in = new ByteArrayInputStream(ba);

            Resource resource = rs.createResource(URI.createURI("dummy:/" + System.currentTimeMillis() + ".xml"), null);
            resource.load(in, rs.getLoadOptions());

            return (NSTA) resource.getContents().get(0);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    static {
        Injector injector = UppaalSMCStandaloneSetup.doSetup();
        rs = injector.getInstance(XtextResourceSet.class);
    }

    @SuppressWarnings("unchecked")
    public MainUI(Registry r) {
        super();

        new JSONParser();
        docr = r.getRepository("EditorDocument");
        tracer = r.getRepository("SymbolicTrace");
        concreteTracer = r.getRepository("ConcreteTrace");
        problemr = r.getRepository("EditorProblems");
        systemr = r.getRepository("SystemModel");
        r.publishRepository(slr = new SanityLogRepository());
        workspaces[0] = this;
        r.addListener(this);

        guir = new GenericRepository<>("GUI");
        guir.set(this);

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));

        String spec = "{}";

        JPanel specPanel = new JPanel();
        specPanel.setLayout(new BorderLayout());
        specPanel.setBorder(BorderFactory.createTitledBorder("Spec"));

        specArea = new JTextArea(spec);

        specArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent documentEvent) {
                EditorUtil.INSTANCE.saveSpecification(specArea.getText(), docr.get());
            }

            @Override
            public void removeUpdate(DocumentEvent documentEvent) {
                EditorUtil.INSTANCE.saveSpecification(specArea.getText(), docr.get());
            }

            @Override
            public void changedUpdate(DocumentEvent documentEvent) {
            }
        });

        JScrollPane scrollPane = new JScrollPane(specArea);
        specPanel.add(scrollPane, BorderLayout.CENTER);
        specPanel.setPreferredSize(new Dimension(400, 200));

        runButton = new JButton("Run selected checks");
        runButton.addActionListener(e -> doCheck(specArea.getText(), true));
        specPanel.add(runButton, BorderLayout.SOUTH);

        resultPane = new JEditorPane();
        resultPane.setEditable(false);
        resultPane.setContentType("text/html");

        specPanel.setPreferredSize(new Dimension(100, 400));
        resultPane.setPreferredSize(new Dimension(100, 50));

        JSplitPane pane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, specPanel, resultPane);
        pane.setResizeWeight(1.0);
        add(pane);

        docr.addListener(this);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(e -> {
            if (e.getKeyCode() == KeyEvent.VK_F6 && e.getID() == KeyEvent.KEY_PRESSED) {

                dialogThread = new Thread(() -> {
                    int option = JOptionPane.showOptionDialog(getRootPane(), "Sanity checker is running",
                            "Sanity checker",
                            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, new String[]{"Ok", "Do not show again"}, "Ok");
                    if (option == 1) {
                        doNotShow = true;
                    }
                });
                if (!doNotShow) {
                    dialogThread.start();
                }
                doCheck(specArea.getText(), false);
                return true;
            }
            return false;
        });
    }

    private boolean doNotShow = false;

    private void doCheck(String spec, boolean fromButton) {
        resultPane.setText("");

        if (checkThread != null && checkThread.isAlive()) {
            checkThread.interrupt();
            UppaalUtil.INSTANCE.getEngine().cancel();
            return;
        }

        Document d = docr.get();
        ArrayList<Problem> problems = problemr.get();
        if (problems != null)
            problems.removeIf((it) -> it instanceof ProblemWrapper);
        runButton.setText("Cancel current check");
        checkThread = new Thread(() -> {
//            HTMLDocument doc = (HTMLDocument) resultPane.getDocument();
//
//            Element body = doc.getElement(doc.getDefaultRootElement(), StyleConstants.NameAttribute, HTML.Tag.BODY);
//
//            resultPane.setText("Checking...");
//            for (HyperlinkListener hl : resultPane.getHyperlinkListeners()) {
//                resultPane.removeHyperlinkListener(hl);
//            }
//
//            ValidationSpec s = new ValidationSpec(spec);
//            s.addValidationListener(result -> {
//                System.out.println("got result");
//
//                StringBuilder sb = new StringBuilder();
////                sb.append(resultPane.getText());
//                sb.append(result.getMessage().replaceAll("<", "&lt;").replaceAll(">", "&gt;"));
//                if (result.getShowTrace() != null) {
//                    String url = "file://" + result.hashCode();
//                    sb.append("<a href=\"").append(url).append("\">Load Trace.</a>");
//                    resultPane.addHyperlinkListener((e) -> {
//                        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
//                            if (e.getURL().toString().equals(url)) {
//                                result.getShowTrace().invoke();
//                            }
//                        }
//                    });
//                }
//                sb.append("<br/>");
//
//                try {
//                    doc.insertAfterEnd(body, "some text");
//                } catch (BadLocationException e) {
//                    e.printStackTrace();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//            });
//            List<SanityCheckResult> results = s.check(d);

            List<SanityCheckResult> results = new ValidationSpec(spec).check(d);

            StringBuilder sb = new StringBuilder();
//            sb.append("<html><body>");
            for (SanityCheckResult r: results) {
                sb.append(r.getMessage().replaceAll("<", "&lt;").replaceAll(">", "&gt;"));
                if (r.getShowTrace() != null) {
                    String url = "file://" + r.hashCode();
                    sb.append("<a href=\"").append(url).append("\">Load Trace.</a>");
                    resultPane.addHyperlinkListener((e) -> {
                        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
                            if (e.getURL().toString().equals(url)) {
                                r.getShowTrace().invoke();
                            }
                        }
                    });
                }
                sb.append("<br/>");
            }
//            sb.append("</body></html>");
            resultPane.setText(sb.toString());

            if (!doNotShow && !fromButton) {
                boolean success = results.stream().allMatch(SanityCheckResult::getSatisfied);
                if (dialogThread != null && dialogThread.isAlive()) {
                    dialogThread.interrupt();
                }
                dialogThread = new Thread(() -> {
                    int option = JOptionPane.showOptionDialog(getRootPane(), "Sanity checker done.\n" +
                                    (success ? "All checks satisfied" : "Violations found, see sanity checker tab for details."),
                            "Sanity checker",
                            JOptionPane.DEFAULT_OPTION, JOptionPane.INFORMATION_MESSAGE, null, new String[]{"Ok", "Do not show again"}, "Ok");
                    if (option == 1) {
                        doNotShow = true;
                    }
                });
                dialogThread.start();
            }
            runButton.setText("Run selected checks");
        });
        checkThread.start();
    }

    private Thread checkThread;
    private Thread dialogThread;

    @Override
    public Icon getIcon() {
        return null;
    }

    @Override
    public String getTitleToolTip() {
        return "Detect commonly made errors";
    }

    @Override
    public Component getComponent() {
        return new JScrollPane(this);
    }

    @Override
    public int getDevelopmentIndex() {
        return 350;
    }

    @Override
    public boolean getCanZoom() {
        return false;
    }

    @Override
    public boolean getCanZoomToFit() {
        return false;
    }

    @Override
    public double getZoom() {
        return zoom;
    }

    @Override
    public void setZoom(double value) {
        zoom = value;
    }

    @Override
    public void zoomToFit() {
    }

    @Override
    public void zoomIn() {
    }

    @Override
    public void zoomOut() {
    }

    @Override
    public void setActive(boolean selected) {
        this.selected = selected;
    }

    @Override
    public PluginWorkspace[] getWorkspaces() {
        return workspaces;
    }

    @Override
    public String getTitle() {
        return "SMC Checker";
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        setActive(selected);

        specArea.setText(EditorUtil.INSTANCE.getSpecification(docr.get()));
    }
}
