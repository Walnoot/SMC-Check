package nl.utwente.ewi.fmt.uppaalSMC.urpal

import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.MainUI
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil
import org.junit.Test

class ParseTest {
    /**
     * Confirm that calls to native functions remain in the model after transforming to NSTA object and back.
     */
    @Test
    fun floatFunctionCall () {
        val doc = PropertyTest.loadDoc("float_function.xml")

        val nsta = MainUI.load(doc)

        val tDoc = UppaalUtil.toDocument(nsta, doc)

        // check if serialised xml is a valid UPPAAL model
        UppaalUtil.compile(tDoc)
    }
}