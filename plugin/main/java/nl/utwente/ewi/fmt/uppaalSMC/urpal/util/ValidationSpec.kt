package nl.utwente.ewi.fmt.uppaalSMC.urpal.util

import com.uppaal.engine.EngineException
import com.uppaal.model.core2.Document
import com.uppaal.model.system.UppaalSystem
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.AbstractProperty
import nl.utwente.ewi.fmt.uppaalSMC.urpal.properties.SanityCheckResult
import nl.utwente.ewi.fmt.uppaalSMC.urpal.ui.MainUI
import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import java.io.IOException

class ValidationSpec(private val spec: String) {
    private val listeners = mutableListOf<ValidationListener>()

    fun check(doc: Document): List<SanityCheckResult> {
        val json = JSONParser().parse(spec) as JSONObject
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

        val keyWords = listOf("checks", "type")

        val checks = json["checks"] as JSONArray
        for (check in checks.map { it as JSONObject }) {
            val properties = HashMap<String, Any>()
            for (obj in listOf(json, check)) {
                for (entry in obj) {
                    if (!keyWords.contains(entry.key)) {
                        properties[entry.key as String] = entry.value!!
                    }
                }
            }

            val type = check["type"] as String
            println(type)
            val prop = AbstractProperty.properties.find { it.shortName() == type }!!
            val result = prop.check(nsta, doc, sys, properties)
            results.add(result)

            for (l in listeners) {
                l.onCheckFinished(result)
            }
        }

        return results
    }

    fun addValidationListener(listener: ValidationListener) {
        listeners.add(listener)
    }
}