package nl.utwente.ewi.fmt.uppaalSMC.urpal

import com.uppaal.model.core2.Document
import com.uppaal.model.core2.PrototypeDocument
import com.uppaal.model.io2.XMLReader
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.DeadlockProperty
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.PostSyncProperty
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheckResult
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.ValidationSpec
import org.apache.commons.io.input.CharSequenceInputStream
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class PropertyTest {
    fun setSystemDeclaration(doc: Document, decl: String) {
        doc.setProperty("system", decl)
    }

    fun runCheck(doc: Document, property: AbstractProperty, expectSatisfied: Boolean, vararg args: Pair<String, String>) {
        val spec = ValidationSpec("{}")
        val config =  spec.addPropertyConfiguartion(property)

        for (entry in args) {
            config.setParameter(entry.first, entry.second)
        }

        val result = spec.check(doc)[0]

        Assert.assertEquals(result.satisfied, expectSatisfied)
    }

    @Test
    fun testDeadlock () {
        val doc = loadDoc("deadlock.xml")

        runCheck(doc, DeadlockProperty(), true)

        setSystemDeclaration(doc, "system deadlocked;")

        runCheck(doc, DeadlockProperty(), false)
    }

    @Test
    fun testSyncPostCondition () {
        val doc = loadDoc("synchronisation_post_condition.xml")

        // check symbolic
        runCheck(doc, PostSyncProperty(), true, Pair("channel", "a"), Pair("condition", "true"))

        // check concrete
        runCheck(doc, PostSyncProperty(), true, Pair("channel", "a"), Pair("condition", "true"), Pair("check_type", "concrete"))
    }

    companion object {
        fun loadDoc(file: String) : Document {
            val xml = String(Files.readAllBytes(File("test/resources/$file").absoluteFile.toPath())).replace(Regex("<!DOCTYPE.*>"), "")
            val doc = XMLReader(CharSequenceInputStream(xml, "UTF-8")).parse(PrototypeDocument())

            return doc;
        }
    }
}