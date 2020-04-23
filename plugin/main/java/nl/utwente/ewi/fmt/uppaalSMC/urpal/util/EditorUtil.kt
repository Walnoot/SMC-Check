package nl.utwente.ewi.fmt.uppaalSMC.urpal.util

import com.uppaal.model.core2.Document
import com.uppaal.model.core2.Query
import com.uppaal.model.system.UppaalSystem
import com.uppaal.model.system.symbolic.SymbolicTrace
import com.uppaal.plugin.Repository
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.MainUI
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JTabbedPane

object EditorUtil {
    private val SPEC_QUERY_NAME = "__spec"

    fun showPane (name: String) {
        var tabPane = findTabbedPane()

        for (i in 0 until tabPane.tabCount) {
            if (tabPane.getTitleAt(i) == name) {
                tabPane.selectedIndex = i
                return
            }
        }

        println("Could not find Simulator tab")
    }

    fun runQueryGUI (query: String, doc: Document, sys: UppaalSystem) {
        // First set an empty trace to clear old traces
        MainUI.getTracer().set(SymbolicTrace())
        MainUI.getTracer().fire(Repository.ChangeType.REPLACED)

        val oldDoc = MainUI.getDocument().get()

        MainUI.getDocument().set(doc)

        // add query to the query list so it can be checked in the GUI
		doc.queryList.addLast(Query(query, "Automatically generated query."))

        MainUI.getDocument().fire(Repository.ChangeType.REPLACED)

        MainUI.getSystemr().set(sys)

        var tabPane: JTabbedPane = findTabbedPane()

        selectLastQuery(tabPane)

        var button = findButton(tabPane, "Get Trace")

        if (button != null) {
            button.doClick()
        } else {
            println("Could not find button")
        }

        MainUI.getDocument().set(oldDoc)
    }

    fun saveSpecification(spec: String, doc: Document) {
        var query = doc.queryList.find { it.formula ==  SPEC_QUERY_NAME}

        if (query == null) {
            query = Query(SPEC_QUERY_NAME, spec)
            doc.queryList.addLast(query)
        } else {
            query.comment = spec
        }
    }

    fun getSpecification(doc: Document): String {
        var query = doc.queryList.find { it.formula ==  SPEC_QUERY_NAME}

        if (query != null) {
            return query.comment
        } else {
            return "{}"
        }
    }

    private fun findTabbedPane(): JTabbedPane {
        var tabPane: JTabbedPane? = null

        // check parents of added panel to find the UPPAAL tab pane
        var curr: Container? = MainUI.getGuir().get()
        while (curr != null) {
            if (curr is JTabbedPane) {
                tabPane = curr
                break
            }

            curr = curr.parent
        }
        return tabPane!!
    }

    private fun findButton (container: Container, name: String): JButton? {
        for (comp in iterateContainer(container)) {
            if (comp is JButton && comp.text == name) {
                return comp
            }
        }

        return null
    }

    private fun selectLastQuery (container: Container) {
        for (comp in iterateContainer(container)) {
            if (comp is JList<*>) {
                if (comp.model.size > 0 && comp.model.getElementAt(0) is Query) {
                    // select last element, which should be the query we added
                    comp.selectedIndex = comp.model.size - 1
                }
            }
        }
    }

    private fun iterateContainer (container: Container): Sequence<Component> {
        return sequence {
            for (comp in container.components) {
                yield(comp)

                if (comp is Container) {
                    yieldAll(iterateContainer(comp))
                }
            }
        }
    }
}