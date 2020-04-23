package nl.utwente.ewi.fmt.uppaalSMC.urpal.util

import com.uppaal.model.core2.*
import com.uppaal.model.core2.lsc.AnchoredElement
import com.uppaal.model.core2.lsc.InstanceLine
import com.uppaal.model.core2.lsc.Message

class DocumentChangeListener (private val listener: () -> Unit) : EventListener {
    override fun messageSourceChanged(p0: Message?, p1: InstanceLine?, p2: InstanceLine?) {
        onChange()
    }

    override fun edgeSourceChanged(p0: Edge?, p1: AbstractLocation?, p2: AbstractLocation?) {
        onChange()
    }

    override fun messageTargetChanged(p0: Message?, p1: InstanceLine?, p2: InstanceLine?) {
        onChange()
    }

    override fun propertyChanged(p0: Property?, p1: String?, p2: Any?, p3: Any?) {
        onChange()
    }

    override fun afterInsertion(p0: Node?, p1: Node?) {
        onChange()
    }

    override fun beforeRemoval(p0: Node?, p1: Node?) {
        onChange()
    }

    override fun edgeTargetChanged(p0: Edge?, p1: AbstractLocation?, p2: AbstractLocation?) {
        onChange()
    }

    override fun afterRemoval(p0: Node?, p1: Node?) {
        onChange()
    }

    override fun afterMove(p0: Node?, p1: Node?) {
        onChange()
    }

    override fun anchorChanged(p0: AnchoredElement?, p1: InstanceLine?, p2: InstanceLine?) {
        onChange()
    }

    private fun onChange () {
        listener.invoke()
    }
}