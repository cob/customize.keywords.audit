import com.cultofbits.customizations.audit.DefinitionAuditorCache

if (msg.user == "integrationm" || msg.product != "recordm" || !(msg.action =~ "add|update")) return

// ===================================================================================================

def updateMap = DefinitionAuditorCache.getAuditorForDefinition(msg, recordm, log)
        .audit(msg)

if (updateMap.size() > 0) {
    recordm.update(msg.type, msg.instance.id, updateMap);
}
