package nl.utwente.ewi.fmt.uppaalSMC.urpal.ui

import com.uppaal.plugin.Repository
import net.miginfocom.swing.MigLayout
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.ArgumentType
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheckResult
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.*
import java.awt.Color
import java.awt.Dimension
import java.awt.Label
import java.awt.event.ItemEvent
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * UI panel with elements that show and update the ValidationSpec state.
 */
class ValidationPanel (private val spec: ValidationSpec): JPanel(), ValidationListener {
    private var checkThread: Thread? = null

    /**
     * Callback per validation config to update UI state during checking.
     */
    private val updateLabels = mutableMapOf<ValidationSpec.PropertyConfiguration, (SanityCheckResult) -> Unit>()

    init {
        layout = MigLayout("wrap 1", "[grow, fill]", "")

        addContent()

        spec.addValidationListener(this)

        // save spec state after every state change
        spec.addChangeListener {
            val specString = spec.toJSON().toJSONString()
            val doc = MainUI.getDocument().get()
            EditorUtil.saveSpecification(specString, doc)
        }

        val listener = DocumentChangeListener {
            println("change!")
        }
        MainUI.getDocument().get().addListener(listener)
    }

    fun update () {
        addContent()
    }

    /**
     * Clears panel components and add components reflecting spec state.
     */
    private fun addContent() {
        removeAll()

        val doc = MainUI.getDocument().get()
        val nsta: NSTA? = MainUI.load(doc)

        val runPanel = JPanel()
        runPanel.layout = MigLayout()
        val runButton = JButton(BUTTON_TEXT_RUN)
        val statusLabel = JLabel()

        if (nsta == null) {
            statusLabel.text = "Unable to parse document."
        } else {
            runButton.addActionListener {
                // TODO: add mutex to avoid any race conditions

                val thread = checkThread

                if (thread == null || !thread.isAlive) {
                    runButton.text = BUTTON_TEXT_CANCEL

                    // run checks in new thread to avoid blocking UI
                    val newThread = Thread {
                        try {
                            val doc = MainUI.getDocument().get()
                            val results = spec.check(doc)
                            val allSatisfied = results.all { it.satisfied }

                            if (allSatisfied) {
                                statusLabel.text = "All checks satisfied."
                                statusLabel.foreground = Color.BLACK
                            } else {
                                statusLabel.text = "Some checks not satisfied."
                                statusLabel.foreground = Color.RED
                            }
                        } finally {
                            runButton.text = BUTTON_TEXT_RUN
                        }
                    }

                    checkThread = newThread
                    newThread.start()
                } else {
                    // cancel check
                    thread.interrupt()
                    UppaalUtil.engine.cancel();
                }
            }

            runPanel.add(runButton)
            runPanel.add(statusLabel)
            add(runPanel)

            val checksPanel = JPanel()
            checksPanel.layout = MigLayout("wrap 1")
            checksPanel.border = BorderFactory.createTitledBorder("Checks")

            for (check in spec.propertyConfigurations) {
                checksPanel.add(checkToPanel(check))
            }

            if (spec.propertyConfigurations.isEmpty()) {
                checksPanel.add(JLabel("Press 'Add Check' to add a validation property."))
            }

            val addCheckButton = JButton("Add check")
            addCheckButton.addActionListener { addCheckDialog() }

            checksPanel.add(addCheckButton)

            add(checksPanel)

            val constantsPanel = getConstantsPanel(nsta)

            add(constantsPanel)

            val timePanel = getTimePanel(nsta)

            add(timePanel)
        }

        invalidate()
        repaint()
    }

    /**
     * Create JPanel to overwrite variable values.
     */
    private fun getConstantsPanel(nsta: NSTA): JPanel {
        val constantsPanel = JPanel()
        constantsPanel.layout = MigLayout("wrap 3", "[100px][100px, fill][100px]")
        constantsPanel.border = BorderFactory.createTitledBorder("Overwrite Constants")

//        val constantsTextArea = JTextArea()
//        constantsTextArea.minimumSize = Dimension(300, 100)
//        constantsPanel.add(constantsTextArea)

        val elements = mutableListOf<Pair<JComboBox<String>, JTextField>>()
        val constants = spec.overrideConstants

        val updateFuction = {
            spec.clearOverrideConstants()
            for ((key, value) in elements) {
                // selected item can be null if the NSTA has zero constant vars
                if (key.selectedItem != null) {
                    spec.setOverrideConstant(key.selectedItem as String, value.text)
                }
            }
        }

        for (entry in constants) {
            val constantVars = UppaalUtil.getConstants(nsta).map { it.name }.toTypedArray()
            val comboBox = JComboBox(constantVars)
            comboBox.selectedItem = entry.key
            comboBox.addActionListener { updateFuction() }
            constantsPanel.add(comboBox)

            val valueField = JTextField(entry.value as String)
            addChangeListener(valueField, updateFuction)
            constantsPanel.add(valueField)

            val pair = Pair(comboBox, valueField)
            elements.add(pair)

            val removeButton = JButton("Remove")
            removeButton.addActionListener {
                elements.remove(pair)
                updateFuction()
                addContent()
            }
            constantsPanel.add(removeButton)
        }

        val addButton = JButton("Add override")
        addButton.addActionListener {
            spec.setOverrideConstant("", "")
            addContent()
        }
        constantsPanel.add(addButton)

        updateFuction()

        return constantsPanel
    }

    private fun checkToPanel(check: ValidationSpec.PropertyConfiguration): JPanel {
        val property = check.property
        val parameters = check.parameters

        val panel = JPanel()
        panel.layout = MigLayout("wrap 2", "[100px][300px, fill, grow]")
        panel.border = BorderFactory.createTitledBorder(property.name())

        for (param in property.getParameters()) {
            val parameter = parameters[param.name] as String?

            panel.add(Label("${param.description}:"))

            when (param.type) {
                ArgumentType.STRING -> {
                    val field = JTextField(parameter)

                    addChangeListener(field) {
                        check.setParameter(param.name, field.text)
                    }

                    panel.add(field)
                }
                ArgumentType.CHECK_TYPE -> {
                    val specKeys = arrayOf("symbolic", "concrete", "concolic")
                    val displayNames = arrayOf("Symbolic", "Concrete", "Hybrid (unimplemented)")

                    val box = JComboBox<String>(displayNames)

                    val index = specKeys.indexOf(parameter)
                    if (index != -1) {
                        box.selectedIndex = index
                    }

                    box.addActionListener {
                        check.setParameter(param.name, specKeys[box.selectedIndex])
                    }

                    // set param to initial value
                    check.setParameter(param.name, specKeys[box.selectedIndex])

                    panel.add(box)
                }
            }
        }

        val statusPanel = JPanel()
        statusPanel.layout = MigLayout("wrap 1", "[250px]")
        panel.add(statusPanel, "span 2")

        updateLabels[check] = { result ->
            statusPanel.removeAll()

            println(result.satisfied)
            println(result.message)

            val label = JLabel(result.message)
            label.foreground = if (result.satisfied) Color.BLACK else Color.RED
            statusPanel.add(label)

            if (result.showTrace != null) {
                val traceButton = JButton("Load trace")
                traceButton.addActionListener { result.showTrace.invoke() }
                statusPanel.add(traceButton)
            }
        }

        val removeButton = JButton("Remove")
        removeButton.addActionListener {
            spec.removePropertyConfiguration(check)
            addContent()
        }
        panel.add(removeButton)

        return panel
    }

    /**
     * Show dialog to add an additional validation property.
     */
    private fun addCheckDialog() {
        val checkTypes = AbstractProperty.properties.map { it.name() }.toTypedArray()
        val option =  JOptionPane.showInputDialog(null, "Checker type", "Checker type", JOptionPane.QUESTION_MESSAGE, null, checkTypes, checkTypes[0])

        if (option != null) {
            val property = AbstractProperty.properties.find { it.name() == option }!!
            spec.addPropertyConfiguartion(property)

            addContent()
        }
    }

    /**
     * Create panel to add time-constraints to the model.
     */
    private fun getTimePanel(nsta: NSTA): JPanel {
        val timePanel = JPanel()
        timePanel.layout = MigLayout("wrap 2")
        timePanel.border = BorderFactory.createTitledBorder("Add time constraint")

        val timeEnabled = spec.timeLimitEnabled

        val checkBox = JCheckBox("Enable time constraint", timeEnabled)
        timePanel.add(checkBox, "span 2")

        if (timeEnabled) {
            timePanel.add(Label("Clock:"))

            val clocks = UppaalUtil.getClocks(nsta).map { it.name }
            val globalTimeValue = "Global time"

            val choices = arrayOf(globalTimeValue) + clocks.toTypedArray()
            val box = JComboBox(choices)
            box.addActionListener {
                if (box.selectedItem.equals(globalTimeValue)) {
                    spec.timeLimitClock = null
                } else {
                    spec.timeLimitClock = box.selectedItem as String
                }
            }
            box.maximumSize = Dimension(200, 50)
            timePanel.add(box)

            timePanel.add(Label("Time:"))
            val timeField = JTextField(spec.timeLimit.toString())
            addChangeListener(timeField) {
                spec.timeLimit = timeField.text
            }
            timePanel.add(timeField, "grow")
        }

        checkBox.addItemListener {
            if (it.stateChange == ItemEvent.SELECTED) {
                spec.timeLimit = "100"
            } else {
                spec.timeLimit = ""
            }

            addContent()
        }
        return timePanel
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

    override fun onCheckFinished(config: ValidationSpec.PropertyConfiguration, result: SanityCheckResult) {
        updateLabels[config]?.invoke(result)
    }

    companion object {
        const val BUTTON_TEXT_RUN = "Run Checks"
        const val BUTTON_TEXT_CANCEL = "Cancel Checks"
    }
}