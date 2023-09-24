package com.cultofbits.customizations.calc

import com.cultofbits.integrationm.service.actionpack.RecordmActionPack
import com.cultofbits.integrationm.service.dictionary.recordm.RecordmMsg
import com.google.common.cache.*

class DefinitionAuditorCache {

    protected static Cache<String, DefinitionAuditor> definitionCache = CacheBuilder.newBuilder()
            .maximumSize(100)
            .build()

    static DefinitionAuditor getAuditorForDefinition(RecordmMsg recordmMsg, RecordmActionPack recordmActionPack, log) {
        String definitionName = recordmMsg.type

        def auditor = getFromCache(definitionName, recordmActionPack, log)
        if (auditor.defVersion == recordmMsg.definitionVersion) {
            return auditor

        } else {
            definitionCache.invalidate(definitionName);
            return getFromCache(definitionName, recordmActionPack, log)
        }
    }

    private static DefinitionAuditor getFromCache(String definitionName, recordmActionPack, log) {
        definitionCache.get(
                definitionName,
                { recordmActionPack.getDefinition(definitionName)?.with { r -> new DefinitionAuditor(r.getBody(), log) } })
    }
}