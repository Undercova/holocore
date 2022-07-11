package com.projectswg.holocore.services.gameplay.combat.command

import com.projectswg.holocore.intents.gameplay.combat.EnterCombatIntent
import com.projectswg.holocore.resources.support.global.commands.CombatCommand
import com.projectswg.holocore.resources.support.objects.swg.SWGObject
import com.projectswg.holocore.resources.support.objects.swg.creature.CreatureObject

object CombatCommandDebuff : CombatCommandHitType {
	override fun handle(source: CreatureObject, target: SWGObject?, combatCommand: CombatCommand, arguments: String) {
		if (target is CreatureObject) {
			if (source.isAttackable(target)) {
				val hateAdd: Int = combatCommand.hateAdd
				target.handleHate(source, hateAdd)
				source.updateLastCombatTime()
				EnterCombatIntent.broadcast(source, target)
				EnterCombatIntent.broadcast(target, source)
			}
		}
	}
}