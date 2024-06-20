package docserver.importador.openalex;

import dialnet.docserver.model.documentos.Documento;
import dialnet.docserver.model.documentos.DocumentoContent;
import dialnet.docserver.model.documentos.DocumentoRepository;
import dialnet.docserver.model.documentos.VersionDocumentoId;
import dialnet.docserver.model.versiones.VersionDocumentoFuenteIdentificador;
import docserver.importador.openalex.mapper.DocumentoOpenAlexMapper;
import java.util.ArrayList;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import openalex.documentos.model.DocumentoOpenalex;
import openalex.documentos.model.DocumentoOpenalexRepository;
import openalex.documentos.model.DocumentosV3Repository;
import openalex.documentos.model.VersionesRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Clase encargada de pasar los documentos de la base de datos intermedia de
 * OpenAlex (openalex.documentos) a la base de datos
 * docserver(docserver.documentos_v2)
 *
 * @author Javier Hernáez Hurtado
 */
@Slf4j
@Component
public class Importador implements ApplicationRunner {

    @Autowired
    private DocumentoOpenalexRepository documentoOpenalexRepository;
    @Autowired
    private DocumentoRepository documentoRepository;
    @Autowired
    private VersionesRepository versionesRepository;
    @Autowired
    private DocumentosV3Repository documentoV3Repository;

    @Override
    public void run(ApplicationArguments args) throws Exception {

        //       DocumentoOpenAlexMapper mapper = new DocumentoOpenAlexMapper();
//
//        documentoOpenalexRepository.findAll().stream()
//                .map(DocumentoOpenalex::getDocumento)
//                .filter(documento -> !existe(documento))
//                .map(this::map)              
//                .forEach(documentoRepository::save);

        ArrayList<String> error = new ArrayList<>();

        for (DocumentoOpenalex documento : documentoOpenalexRepository.findAll()) {
            try {
                if (!existe(documento.getDocumento())) {
                    Documento documentoDocserver = map(documento.getDocumento());
                    //documentoRepository.save(documentoDocserver);
                } else {
                    log.info("el documento esta ya en la colección: " + documento.getDocumento().getId());
                }

            } catch (Exception e) {
                log.error("Error al mapear el documento " + documento.getDocumento().getId() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Transforma el documento openAlex en el documento a insertar en
     * documentos_v2
     *
     * @param documentoOpenalex
     * @return
     */
    private Documento map(openalex.documentos.model.Documento documentoOpenalex) {
        DocumentoOpenAlexMapper mapper = new DocumentoOpenAlexMapper();
        DocumentoContent documentoContent = mapper.map(documentoOpenalex);
        VersionDocumentoId versionDocumentoId = new VersionDocumentoId(documentoOpenalex.getId().replace("https://openalex.org/", ""));

        VersionDocumentoFuenteIdentificador versionInicialFuenteIdentificador
                = new VersionDocumentoFuenteIdentificador(
                        VersionDocumentoFuenteIdentificador.Sistema.OPENALEX,
                        documentoOpenalex.getId().replace("https://openalex.org/", ""));

        if (documentoContent != null) {
            log.info("documento mapeado: " + documentoOpenalex.getId());
            return new Documento(documentoContent, versionDocumentoId, versionInicialFuenteIdentificador);
        } else {
            log.info("No se ha mapeado: " + documentoOpenalex.getId());
            return null;
        }
    }

    /**
     * Comprueba si existe el documento en la colección documentos_v3 o
     * versiones_v3
     *
     * @param documento
     * @return
     */
    private boolean existe(openalex.documentos.model.Documento documento) {
        return Optional.ofNullable(documento)
                .map(DocumentoOpenAlexMapper::crearIdentificadoresDocumento).stream()
                .flatMap(identificadores -> identificadores.stream())
                .anyMatch(id
                        -> documentoV3Repository.existsByIdentificador(id.getSistema().name(), id.getId())
                || versionesRepository.existsByIdentificador(id.getSistema().name(), id.getId()));
    }
}
