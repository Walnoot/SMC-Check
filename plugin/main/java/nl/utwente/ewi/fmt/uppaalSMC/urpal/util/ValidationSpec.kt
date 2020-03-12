package nl.utwente.ewi.fmt.uppaalSMC.urpal.util

import com.uppaal.engine.EngineException
import com.uppaal.model.core2.Document
import com.uppaal.model.system.UppaalSystem
import net.miginfocom.swing.MigLayout
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.ArgumentType
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheckResult
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.MainUI
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.awt.Component
import java.awt.Dimension
import java.awt.Label
import java.io.IOException
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class ValidationSpec(spec: String) {
    private val listeners = mutableListOf<ValidationListener>()

    private val json: JSONObject = JSONParser().parse(spec) as JSONObject

    fun check(doc: Document): List<SanityCheckResult> {
        val results = mutableListOf<SanityCheckResult>()

        val nsta = MainUI.load(doc)
        val sys: UppaalSystem
        try {
            sys = UppaalUtil.compile(doc)
        } catch (e: EngineException) {
            e.printStackTrace()
//            output?.writeText("compile error\n")
            return emptyList()
        } catch (e: IOException) {
            e.printStackTrace()
            return emptyList()
        }


        val checks = json["checks"] as JSONArray
        for (check in checks.map { it as JSONObject }) {
            val (prop, parameters) = getCheck(check)
            val result = prop.check(nsta, doc, sys, parameters)
            results.add(result)

            for (l in listeners) {
                l.onCheckFinished(result)
            }
        }

        return results
    }

    private fun getCheck(check: JSONObject): Pair<AbstractProperty, HashMap<String, Any>> {
        val keyWords = listOf("checks", "type")
        val parameters = HashMap<String, Any>()
        for (obj in listOf(json, check)) {
            for (entry in obj) {
                if (!keyWords.contains(entry.key)) {
                    parameters[entry.key as String] = entry.value!!
                }
            }
        }

        val type = check["type"] as String
        println(type)
        val prop = AbstractProperty.properties.find { it.shortName() == type }!!
        return Pair(prop, parameters)
    }

    fun toPanel () : JPanel {
        val rootPanel = JPanel()
        rootPanel.layout = MigLayout("wrap 1", "[grow, fill]", "")

        val checksPanel = JPanel()
        checksPanel.layout = MigLayout("wrap 1")
        checksPanel.border = BorderFactory.createTitledBorder("Checks")

        val syncPanel = JPanel()
        syncPanel.layout = MigLayout("wrap 2")
        syncPanel.border = BorderFactory.createTitledBorder("Receive synchronisations")

        syncPanel.add(Label("Check type:"))
        syncPanel.add(JComboBox(arrayOf("Concrete", "Symbolic", "Hybrid")), "grow")

        syncPanel.add(Label("Channel:"))
        syncPanel.add(JComboBox(arrayOf("a", "b", "c")), "grow")

        syncPanel.add(Label("Template:"))
        syncPanel.add(JComboBox(arrayOf("T1", "T2", "T3")), "grow")

        syncPanel.add(Label("Ignore condition:"))
        syncPanel.add(JTextField(), "grow")

        syncPanel.add(JButton("Remove"))

        checksPanel.add(syncPanel)

        for (check in (json["checks"] as JSONArray).map { it as JSONObject }) {
            checksPanel.add(checkToPanel(check))
        }

        val addCheckButton = JButton("Add check")
        addCheckButton.addActionListener { addCheckDialog() }

        checksPanel.add(addCheckButton)

        rootPanel.add(checksPanel)

        val constantsPanel = JPanel()
        constantsPanel.layout = MigLayout()
        constantsPanel.border = BorderFactory.createTitledBorder("Overwrite Constants")

        val constantsTextArea = JTextArea()
        constantsTextArea.minimumSize = Dimension(300, 100)

        constantsPanel.add(constantsTextArea)

        rootPanel.add(constantsPanel)

        val timePanel = JPanel()
        timePanel.layout = MigLayout("wrap 2")
        timePanel.border = BorderFactory.createTitledBorder("Add time constraint")

        val checkBox = JCheckBox("Enable time constraint")
        timePanel.add(checkBox, "span 2")

        timePanel.add(Label("Clock:"))

        val choices = arrayOf("Global time", "clock1", "clock2")
        val box = JComboBox(choices)
        box.maximumSize = Dimension(200, 50)
        timePanel.add(box)

        timePanel.add(Label("Time:"))
        timePanel.add(JTextField(), "grow")

        rootPanel.add(timePanel)

        return rootPanel
    }

    private fun addCheckDialog() {
        val checkTypes = AbstractProperty.properties.map { it.name() }.toTypedArray()
        val option =  JOptionPane.showInputDialog(null, "Check type", "idk", JOptionPane.QUESTION_MESSAGE, null, checkTypes, checkTypes[0])

        if (option != null) {

        }
    }

    private fun checkToPanel(check: JSONObject): JPanel {
        val (property, parameters) = getCheck(check)

        val panel = JPanel()
        panel.layout = MigLayout("wrap 2", "[100px][100px, fill, grow]")
        panel.border = BorderFactory.createTitledBorder(property.name())

        for (arg in property.getArguments()) {
            val parameter = parameters[arg.name] as String?

            val component: Component? = when (arg.type) {
                ArgumentType.STRING -> {
                    val field = JTextField(parameter)

                    addChangeListener(field) {
                        parameters[arg.name] = field.text
                        println(parameters)
                    }

                    field
                }
                else -> null
            }

            panel.add(Label("${arg.description}:"))
            panel.add(component)
        }

        panel.add(JButton("Remove"))

        return panel
    }

    /**
     * Convenience function to add a listener to a text field that is called on every document change.
     */
    private fun addChangeListener (textField: JTextField, listener: (() -> Unit)) {
        textField.document.addDocumentListener(object : DocumentListener {
            override fun changedUpdate(p0: DocumentEvent?) {
                listener()
            }

            override fun insertUpdate(p0: DocumentEvent?) {
                listener()
            }

            override fun removeUpdate(p0: DocumentEvent?) {
                listener()
            }
        })
    }

    fun addValidationListener(listener: ValidationListener) {
        listeners.add(listener)
    }
}