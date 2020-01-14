package nl.utwente.ewi.fmt.uppaalSMC.urpal.properties

import com.uppaal.model.core2.Document
import com.uppaal.model.core2.PrototypeDocument
import nl.utwente.ewi.fmt.uppaalSMC.NSTA
import nl.utwente.ewi.fmt.uppaalSMC.urpal.util.UppaalUtil

@SanityCheck(name = "Deadlocks", shortName = "deadlock")
class DeadlockProperty : SafetyProperty() {
    override fun translateNSTA(nsta: NSTA, properties: Map<String, Any>): String {
        // compile UppaalSystem so we can iterate over every instantiated process
        val sys = UppaalUtil.compile(UppaalUtil.toDocument(nsta, Document(PrototypeDocument())))

        val locs = mutableListOf<String>()

        for (process in sys.processes) {
            // find corresponding NSTA template
            val template = nsta.template.find { it.name == process.template.getPropertyValue("name") }!!

            for (loc in template.location) {
                // check if there are no edges with this Location as source
                if (nsta.template.flatMap { it.edge }.none { e -> e.source == loc }) {
                    locs.add("${process.name}.${loc.name}")
                }
            }
        }

        val query = if (locs.isEmpty()) {
            "deadlock"
        } else {
            "deadlock and not (${locs.joinToString(" or ")})"
        }

        return query
    }
}
