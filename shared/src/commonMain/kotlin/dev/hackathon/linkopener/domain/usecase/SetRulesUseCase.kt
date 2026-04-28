package dev.hackathon.linkopener.domain.usecase

import dev.hackathon.linkopener.core.model.UrlRule
import dev.hackathon.linkopener.domain.repository.SettingsRepository

class SetRulesUseCase(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(rules: List<UrlRule>) = repository.setRules(rules)
}
