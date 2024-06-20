package docserver.importador.openalex.mapper;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.thoughtworks.xstream.converters.ConversionException;
import dialnet.docserver.model.documentos.*;
import dialnet.docserver.model.documentos.Idioma;
import dialnet.docserver.model.documentos.Acceso.AccesoTipo;
import dialnet.docserver.model.documentos.DocumentoIdentificador.DocumentoIdentificadorSistema;
import dialnet.docserver.model.documentos.DocumentoSource.*;
import dialnet.docserver.model.documentos.SourceIdentificador.SourceIdentificadorSistema;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Month;
import java.util.*;

import static dialnet.docserver.model.documentos.DocumentoContent.FasePublicacion.DEFINITIVO;
import java.time.LocalDate;
import java.time.Year;
import java.util.stream.Collectors;
import static java.util.stream.Collectors.toMap;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import openalex.documentos.model.Author;
import openalex.documentos.model.Authorships;
import openalex.documentos.model.Biblio;
import openalex.documentos.model.Keyword;
import openalex.documentos.model.Location;
import openalex.documentos.model.Documento;
import openalex.documentos.model.Institution;
import openalex.documentos.model.OpenAccess;
import openalex.documentos.model.Source;

/**
 * Esta clase convierte un documento de Scopus que proviene de la respuesta de
 * su API en un DocumentoContent del docserver
 *
 * @author Javier Hernáez Hurtado
 */
@Slf4j
@Component
//@Slf4jauthorGroupAffiliation
public class DocumentoOpenAlexMapper {

    public DocumentoContent map(Documento response) throws ConversionException {

        log.debug("Convirtiendo el documento {} al modelo de docserver", response.getId());

        DocumentoType docType = mapDocumentoType(response);

        SourceType sourceType = mapSourceType(response.getPrimaryLocation().getSource().getType());

        // No continuo si no son tipos de documento o source contemplados en docserver
        if (docType == null || sourceType == null) {
            return null;
        }

        List<Enlace> enlaces = crearEnlaces(response);
        List<Referencia> referencias = crearReferencias(response);

        AfiliacionesHolder afiliacionesHolder = extractAfiliaciones(response);
        List<Autor> autores = crearAutores(response, afiliacionesHolder);
        
        Idioma i;
        
        if(response.getLanguage() != null){
            i= Idioma.of(response.getLanguage());
        }else{
            i = null;
        }
        
        return new DocumentoContent(
            docType,
            crearSource(response),
            crearTitulo(response),
            null,
            i, //idioma
            autores,
            afiliacionesHolder.getAfiliaciones(),
            crearResumenes(response),
            crearIdentificadoresDocumento(response),
            enlaces,
            referencias,
            crearKeywords(response),
            null, //licencia
            crearAcceso(response.getOpenAccess()),
            null,//Financiación
            null, //TODO: completar crearConferenceInfo(response)
            this.getFasePublicacion(response)
    );
            
        
    }

    private List<Referencia> crearReferencias(Documento documentoOpenAlex) {
        List<Referencia> referencias = new ArrayList<>();
        for (String referencia : documentoOpenAlex.getReferencedWorks()) {
            referencias.add(new Referencia(referencia, null, List.of(new DocumentoIdentificador(DocumentoIdentificadorSistema.OPENALEX, referencia.replace("https://openalex.org/", ""))), null, null, null, null, null, null, null));
        }
        return referencias;
    }

    private List<Enlace> crearEnlaces(Documento documento) {
        List<Enlace> enlaces = new ArrayList<>();

        for (Location location : documento.getLocations()) {
            if (location.getLandingPageUrl() != null) {
                if (location.isIsOa()) {
                    enlaces.add(new Enlace(location.getLandingPageUrl(), Enlace.EnlaceTipo.LANDING_PAGE, Enlace.EnlaceTipoAcceso.LIBRE, null));
                } else {
                    enlaces.add(new Enlace(location.getLandingPageUrl(), Enlace.EnlaceTipo.LANDING_PAGE, Enlace.EnlaceTipoAcceso.RESTRINGIDO, null));
                }
            }
        }

        return enlaces;
    }

    private Acceso crearAcceso(OpenAccess open_access) {
        Acceso acceso = null;
        if (open_access != null) {
            if (open_access.getIsOa()) {
                acceso = new Acceso(AccesoTipo.openAccess);
            } else {
                acceso = new Acceso(AccesoTipo.closedAccess);
            }
        }

        return acceso;
    }

    private List<String> crearKeywords(Documento documento) {

        List<String> kewywords = new ArrayList<>();

        if (documento.getKeywords() != null) {
            for (Keyword k : documento.getKeywords()) {
                kewywords.add(k.getDisplayName());
            }
        }
        return kewywords;
    }

    /**
     * Cogemos como principal el primero de los original si es que vienen más de
     * uno. Si no encuentra ninguno original, coge el primero que haya En caso
     * de no existir ninguno en scopus pondremos el texto "Sin título en SCOPUS"
     * Revisar
     */
    private Titulo crearTitulo(Documento documento) {
        //el titulo no tiene  idioma
        //Idioma idioma= new Idioma(publicacion.getLanguage(), null);
        if (documento.getTitle() != null) {
            if (documento.getLanguage() != null) {
                return new Titulo(documento.getTitle(), null, Idioma.of(documento.getLanguage()));
            } else {
                return new Titulo(documento.getTitle(), null, null);
            }
        } else {
            return new Titulo("Sin título en OpenAlex", null, null);
        }
    }

    private List<Resumen> crearResumenes(Documento documento) {

        List<Resumen> resumenes = new ArrayList<>();
        if (documento.getAbstractInvertedIndex() == null) {
            return Collections.emptyList();
        }
        if (documento.getLanguage() != null) {
            resumenes.add(new Resumen(documento.getAbstractInvertedIndex(), Idioma.of(documento.getLanguage())));
        } else {
            resumenes.add(new Resumen(documento.getAbstractInvertedIndex(), null));
        }
        //el abstract no tiene idioma, asi que le pongo el del propio documento

        return resumenes;
    }

    /**
     * Necesitamos la lista de afiliaciones calculada previamente para enlazar
     * con los códigos
     */
    private List<Autor> crearAutores(Documento documento, AfiliacionesHolder afiliacionesHolder) {

        if (documento.getAuthorships() != null) {

            LinkedHashMap<String, Autor> autores = new LinkedHashMap<>();

            for (Authorships authorship : documento.getAuthorships()) {

                Autor autor = this.mapAutor(authorship, authorship.getAuthor(), afiliacionesHolder);

                if (autor != null) {
                    autores.put(authorship.getAuthor().getId().replace("https://openalex.org/", ""), autor);
                }

            }

            return Lists.newArrayList(autores.values());

        } else {

            return new ArrayList<Autor>();

        }
    }

    private AfiliacionesHolder extractAfiliaciones(Documento documento) {

        AfiliacionesHolder afiliacionesHolder = new AfiliacionesHolder();
        if (documento.getAuthorships() != null) {
            documento.getAuthorships()
                    .forEach(afiliacionesHolder::add);
        }

        return afiliacionesHolder;
    }

    @Value
    private static class AfiliacionesHolder {

        private List<Afiliacion> afiliaciones = new ArrayList<>();
        private ArrayListMultimap<String, AfiliacionId> autoresAfiliaciones = ArrayListMultimap.create();

        private void add(Authorships authorship) {

            if (authorship == null) {
                return;
            }

            // FIXME IMPORTANTE: ¿que hacer con las afiliaciones que no tienen organizacion o text?
            // db.getCollection('documentos').find(
            //    {"documento.item.bibrecord.head.authorGroups": { $elemMatch: { "affiliation.afid": {$exists:true}, "affiliation.organization.0":{$exists:false}, "affiliation.text": null }}},
            //    {"documento.item.bibrecord.head.authorGroups.affiliation":1}
            //)
            if (authorship.getInstitutions() != null){
                for (Institution institucion : authorship.getInstitutions()) {
                    if (!estaAfiliacion(institucion, afiliaciones)) {
                        int generatedId = afiliaciones.size() + 1;
                        Afiliacion afiliacion = new Afiliacion(
                                new AfiliacionId("af" + generatedId),
                                institucion.getDisplayName(), // literal
                                null,
                                null,//pais
                                null, // organizacionId
                                null, // OrganizacionIdManual
                                null, // organizacionIdComputado
                                null, // email
                                null, // website
                                null,
                                null,
                                null,
                                null,
                                List.of(new Afiliacion.AfiliacionIdentificador(Afiliacion.AfiliacionIdentificadorSistema.OPENALEX, institucion.getId().replace("https://openalex.org/", "")),
                                        new Afiliacion.AfiliacionIdentificador(Afiliacion.AfiliacionIdentificadorSistema.ROR, institucion.getRor().replace("https://ror.org/", "")))
                        );
                        // Añado la afiliacion del AuthorGroup
                        afiliaciones.add(afiliacion);

                        // Añado las relaciones entre los autores y la afiliacion del AuthorGroup siempre y cuando exista autor
                        if (authorship.getAuthor() != null) {
                            autoresAfiliaciones.put(authorship.getAuthor().getId(), afiliacion.getAfiliacionId());
                        }
                    } else {
                        // Añado las relaciones entre los autores y la afiliacion del AuthorGroup siempre y cuando exista autor
                        for (Afiliacion afiliacion : afiliaciones) {
                            if (afiliacion.getLiteral().equalsIgnoreCase(institucion.getDisplayName())) {
                                autoresAfiliaciones.put(authorship.getAuthor().getId(), afiliacion.getAfiliacionId());
                            }
                        }
                    }
                }
            }
        }

        List<AfiliacionId> getAutorAfiliaciones(String id) {
            return autoresAfiliaciones.get(id);
        }

        private boolean estaAfiliacion(Institution institucion, List<Afiliacion> afiliaciones) {

            for (Afiliacion afiliacion : afiliaciones) {
                if (afiliacion.getLiteral().equalsIgnoreCase(institucion.getDisplayName())) {
                    return true;
                }
            }
            return false;
        }

    }

    //bien
    private Pages mapPages(Biblio biblio) {
        return (biblio != null) ? new Pages(biblio.getFistPage(), biblio.getLastPage()) : null;
    }

    
    private List<SourceIdentificador> crearIdentificadoresSource(Source source) {
        if (source.getId() != null) {
            return List.of(new SourceIdentificador(SourceIdentificadorSistema.OPENALEX, source.getId().replace("https://openalex.org/", "")));//preguntar si poner este id o no
        } else {
            return null;
        }
    }

    private DocumentoSource crearSource(Documento documento) {

        Source openalexSrc = documento.getPrimaryLocation().getSource();

        SourceType tipoSource = mapSourceType(documento.getPrimaryLocation().getSource().getType());

        List<ISSN> issn = new ArrayList<>();
        
        if(openalexSrc.getIssn() != null){
            issn = openalexSrc.getIssn().stream()
                .map(issn1 -> new ISSN(ISSN.Type.DESCONOCIDO, issn1))
                .collect(Collectors.toList());
        }

        List<ISBN> isbn = new ArrayList<>();
        PublicationYear publicationYear = new PublicationYear(documento.getPublicationYear(), null);
        LocalDate fecha = LocalDate.parse(documento.getPublicationDate());

        String fechaString = documento.getPublicationDate(); // La fecha en formato "YYYY-MM-DD"

        // Dividir la cadena en año, mes y día
        String[] partesFecha = fechaString.split("-");
        int yearValue = Integer.parseInt(partesFecha[0]);
        int monthValue = Integer.parseInt(partesFecha[1]);
        int dayValue = Integer.parseInt(partesFecha[2]);

        // Crear objetos Year, Month y Integer a partir de los valores obtenidos
        Year year = Year.of(yearValue);
        Month month = Month.of(monthValue);
        Integer day = dayValue;
        PublicationDate publicationDate = new PublicationDate(year, month, day);

        List<Editor> editores = new ArrayList<>();
        if (documento.getPrimaryLocation().getSource().getHostOrganizationName() != null) {
            editores.add(new Editor(null, documento.getPrimaryLocation().getSource().getHostOrganizationName(), null, null, null, null, null));
        }

        List<Autor> autores = new ArrayList<>();

        String volumen = Optional.ofNullable(documento.getBiblio())
                .map(Biblio::getVolume)
                .orElse("");
        String issue = Optional.ofNullable(documento.getBiblio())
                .map(Biblio::getVolume)
                .orElse("");

        VolumeIssue volumeIssue = new VolumeIssue(new Volume(null, volumen, null), new Issue(null, issue, null), null);

        String firstPage = Optional.ofNullable(documento.getBiblio())
                .map(Biblio::getFistPage)
                .orElse("");
        String lastPage = Optional.ofNullable(documento.getBiblio())
                .map(Biblio::getLastPage)
                .orElse("");

        Pages pages = new Pages(firstPage, lastPage);

        DocumentoSource src = new DocumentoSource(null,
                tipoSource,
                openalexSrc.getDisplayName(),
                issn,
                isbn,
                publicationYear,
                null,
                volumeIssue,
                pages,
                publicationDate,
                null,
                editores,
                autores,
                crearIdentificadoresSource(openalexSrc));

        return src;
    }

   
    private DocumentoType mapDocumentoType(Documento documento) {
        // Lo hago con un switch porque es más potente que un
        // mapeo directo con una enumeración
        DocumentoType st = null;

        if (documento.getType() != null) {
            switch (documento.getType()) {
                case "dissertation":
                    st = DocumentoType.DISSERTATION; //esta esta en openAlex
                    break;
                case "article":
                    st = DocumentoType.ARTICLE;
                    break;
                case "report":
                    st = DocumentoType.REPORT;
                    break;
                case "book":
                    st = DocumentoType.BOOK;
                    break;
                case "book-chapter":
                    st = DocumentoType.BOOK_CHAPTER;
                    break;
                case "editorial":
                    st = DocumentoType.EDITORIAL;
                    break;
                case "erratum":
                    st = DocumentoType.ERRATUM;
                    break;
                case "letter":
                    st = DocumentoType.LETTER;
                    break;
                case "review":
                    st = DocumentoType.REVIEW;
                    break;
                default:
                    log.error("El tipo de documento '{}' no tiene conversión al modelo del doc-server ", documento.getType());
                    throw new IllegalArgumentException("El tipo de documento " + documento.getType() + " no tiene conversión al modelo del doc-server");
            }
        }
        return st;
    }

    //no esta el ebook platform y repository
    private SourceType mapSourceType(String srcType) {

        SourceType st = null;
        if (!srcType.isEmpty() || srcType != null) {
            switch (srcType) {
                case "journal":
                    st = SourceType.JOURNAL;
                    break;
                case "conference":
                    st = SourceType.CONFERENCE_PROCEEDING;
                    break;
                case "book series":
                    st = SourceType.BOOK_SERIES;
                    break;
                default:
                    log.error("El tipo de documento-source '{}' no tiene conversión al modelo del doc-server ", srcType);
                    throw new IllegalArgumentException("El tipo de documento-source " + srcType + " no tiene conversión al modelo del doc-server");
            }
        } else {
            log.error("El tipo de documento-source es null por lo que no tiene conversión al modelo del doc-server ");
            throw new IllegalArgumentException("El tipo de documento-source es null por lo que no tiene conversión al modelo del doc-server ");
        }
        return st;
    }

    private List<Autor.AutorIdentificador> crearIdentificadoresAutor(Author autor) {

        List<Autor.AutorIdentificador> identificacionesAutor = new ArrayList<>();
        identificacionesAutor.add( new Autor.AutorIdentificador(Autor.AutorIdentificadorSistema.OPENALEX, autor.getId().replace("https://openalex.org/", ""))
        );

        if (autor.getOrcid() != null) {
            identificacionesAutor.add(new Autor.AutorIdentificador(Autor.AutorIdentificadorSistema.ORCID, autor.getOrcid().replace("https://orcid.org/", "")));
        }

        return identificacionesAutor;

    }

    private Autor mapAutor(Authorships authorship, Author author, AfiliacionesHolder afiliacionesHolder) {

        if (!author.getDisplayName().equalsIgnoreCase("NULL AUTHOR_ID") || authorship.getRawAuthorName() != null || !authorship.getRawAuthorName().equalsIgnoreCase("")) {

            String literal = Optional.ofNullable(authorship)
                    .map(Authorships::getRawAuthorName)
                    .orElse(Optional.ofNullable(author.getDisplayName()).orElse("Sin literal"));

            String nombre = Optional.ofNullable(author.getDisplayName())
                    .orElse("Sin nombre");

            return new Autor(
                    null,
                    null,
                    null,
                    literal,
                    nombre,
                    null,
                    null,
                    null,
                    Autor.AutorType.AUT,
                    afiliacionesHolder.getAutorAfiliaciones(author.getId()),
                    crearIdentificadoresAutor(author));
        } else {
            return null;
        }
    }

    
    enum IdentificadoresSoportados {
        SCOPUS(DocumentoIdentificadorSistema.SCOPUS),
        SGR(DocumentoIdentificadorSistema.SGR),
        SCP(DocumentoIdentificadorSistema.SCP),
        PUI(DocumentoIdentificadorSistema.PUI),
        DBCOL(DocumentoIdentificadorSistema.DBCOOL),
        DOI(DocumentoIdentificadorSistema.DOI),
        ARXIV(DocumentoIdentificadorSistema.ARXIV),
        MEDL(DocumentoIdentificadorSistema.MEDL),;

        DocumentoIdentificadorSistema sistema;

        IdentificadoresSoportados(DocumentoIdentificadorSistema sistema) {
            this.sistema = sistema;
        }

        static Optional<IdentificadoresSoportados> valorDe(String tipoIdentificador) {
            return Arrays.stream(values())
                    .filter(v -> tipoIdentificador.equals(v.name()))
                    .findFirst();

        }

        //                case "FRAGMENTID":
        //                case "CHEM":
        //                case "EMBASE":
        //                case "REAXYS":
        //                case "REAXYSCAR":
        //                case "ARTNUM":
        //                case "SNEMB":
        //                case "MOSYEA":
        //                case "SNCABS":
        //                case "CAR-ID":
        //                case "CPX":
        //                case "FLX":
        //                case "TPA-ID":
        //                case "APILIT":
        //                case "EMBIO":
        //                case "GEO":
        //                case "CABS":
        //                case "RMC":
        //                case "PUIsecondary":
        //                case "SNPSYB":
        //                case "SNSOC":
        //                case "CODENCODE":
        //                case "SNARHU":
        //                case "SNCHEM":
        //                case "NURSNG":
        //                case "BSTEIN":
        //                case "SNMATH":
        //                case "SNGEO":
        //                case "SNCPX":
        //                case "ARCEDP":
        //                case "ARCAMS":
        //                case "SCOGAP":
        //                case "SNPHYS":
        //                case "ISSN":
        // ADONIS
        //Adonis collection
        //
        //APILIT
        //API Technical Literature
        // 
        //APINWS
        //API Business News
        //
        //CABS
        // 
        //CABS collection
        // 
        //CHEM
        //Chemistry collection
        //
        //CPX
        //Engineering Information/Compendex
        // 
        //ECON
        //Econmics Literature (Econlit)
        //
        //EMBASE
        //EMBASE collection
        //
        //EST
        //Environmental Science and Technology
        // 
        //FLX
        //FLUIDEX
        //
        //GEO
        //GEObase
        // 
        //MEDL
        //Medline
        //
        //PSYC
        //Psychology (PsycINFO)
        //
        //SCP
        //Scopus
        //
        //SGR
        //Scopus Group
        // 
        //SNCABS
        //SN Agriculture, Biology & Environmental Science
        //
        //SNCHEM
        //SN Chemistry
        //
        //SNCPX
        //SN Engineering & Technology
        // 
        //SNECON
        //SN Business & Economics
        // 
        //SNEMB
        //SN Biomedicine
        // 
        //SNGEO
        //SN Earth Science
        // 
        //SNMATH
        //SN Mathematics
        // 
        //SNPHYS
        //SN Physics
        //
        //SNPSYB
        //SN Psychology & Behavioral Science
        // 
        //SNSOC
        //SN Social Science
        // 
        //WTA
        //World Textiles
        // 
    }

    public static List<DocumentoIdentificador> crearIdentificadoresDocumento(Documento documento) {

        List<DocumentoIdentificador> ids = new ArrayList<>();
        //DOI
        if (documento.getDoi() != null) {
//            log.info("El documento {} no tiene DOI", documento.getId());
//        } else {
            ids.add(new DocumentoIdentificador(DocumentoIdentificadorSistema.DOI,
                    documento.getDoi().replace("https://doi.org/", "").toUpperCase()));
        }

        //OPENALEX
        Optional.ofNullable(documento.getId().replace("https://openalex.org/", ""))
                .map(id -> new DocumentoIdentificador(DocumentoIdentificadorSistema.OPENALEX, id))
                .ifPresent(ids::add);

        //PMID
        if (documento.getIds().getPmid() != null) {
            Optional.ofNullable(documento.getIds().getPmid().replace("https://pubmed.ncbi.nlm.nih.gov/", ""))
                    .map(id -> new DocumentoIdentificador(DocumentoIdentificadorSistema.PMID, id))
                    .ifPresent(ids::add);
        }

        //PMCID
        if (documento.getIds().getPmcid() != null) {
            Optional.ofNullable(documento.getIds().getPmcid().replace("https://www.ncbi.nlm.nih.gov/pmc/articles/", ""))
                    .map(id -> new DocumentoIdentificador(DocumentoIdentificadorSistema.PMCID, id))
                    .ifPresent(ids::add);
        }

        //MAG
        if (documento.getIds().getMag() != null) {
            Optional.ofNullable(documento.getIds().getMag())
                    .map(id -> new DocumentoIdentificador(DocumentoIdentificadorSistema.MAG, id))
                    .ifPresent(ids::add);
        }
        return ids;

    }

    //preguntar, poner todo Definitivo
    private DocumentoContent.FasePublicacion getFasePublicacion(Documento documento) {
        /*if ("ip".equals(documento.getCoredata().getSubtype())) {
            return EN_PROCESO;
        } else if (!"S300".equals(documento.getItem().getProcessInfo().getStatus().getStage())) {
            return EN_PROCESO;
        } else {
            return DEFINITIVO;
        }*/
        return DEFINITIVO;
    }

//    private List<Afiliacion> crearAfiliaciones(List<Authorships> authorships) {
//        List<Afiliacion> afiliaciones = new ArrayList<>();
//
//        if (authorships != null) {
//            for (Authorships authorship : authorships) {
//                if (authorship.getInstitutions() != null) {
//                    for (Institution i : authorship.getInstitutions()) {
//                        if (!estaAfiliacion(i, afiliaciones)) {
//                            int generatedId = afiliaciones.size() + 1;
//                            afiliaciones.add(
//                                    new Afiliacion(
//                                            new AfiliacionId("af" + generatedId),
//                                            i.getDisplayName(), // literal
//                                            null, // nombre
//                                            null,
//                                            null, // organizacionId
//                                            null, // OrganizacionIdManual
//                                            null, // organizacionIdComputado
//                                            null, // email
//                                            null, // website
//                                            null,
//                                            null,
//                                            null,
//                                            null,
//                                            List.of(new Afiliacion.AfiliacionIdentificador(Afiliacion.AfiliacionIdentificadorSistema.OPENALEX, i.getId().replace("https://openalex.org/", "")),
//                                                     new Afiliacion.AfiliacionIdentificador(Afiliacion.AfiliacionIdentificadorSistema.ROR, i.getRor().replace("https://ror.org/", ""))))
//                            );
//                        }
//                    }
//                }
//            }
//        }
//        return afiliaciones;
//    }
//    private List<Autor> crearAutores(List<Authorships> authorships, AfiliacionesHolder afiliacionesHolder) {
//        List<Autor> autores = new ArrayList<>();
//
//        if (authorships != null) {
//            for (Authorships authorship : authorships) {
//
//                Author a = authorship.getAuthor();
//                List<AfiliacionId> afiliacionesId = authorship.getInstitutions().stream()
//                        .map(institution -> new AfiliacionId(institution.getId()))
//                        .collect(Collectors.toList());
//                
//                String literal= Optional.ofNullable(authorship)
//                                .map(Authorships::getRawAuthorName)
//                                .orElse(a.getDisplayName());
//
////                String nombre = "";
////                String apellido = "";
////
////                String literal = "";
////                String iniciales = "";
////
////                if (a.getDisplayName() != null) {
////                    String nombreCompleto = a.getDisplayName();
////                    String[] nombre_apellido = nombreCompleto.split(" ");
////
////                    nombre = nombre_apellido[0];
////                    apellido = nombre_apellido[1];
////
////                    literal = apellido + ", " + nombre.charAt(0) + ".";
////                    iniciales = nombre.charAt(0) + ".";
////                }
//
    //                //autores.add(new Autor(null, null, null, literal, nombre, apellido, iniciales, null, Autor.AutorType.AUT, afiliacionesId, crearIdentificadoresAutor(a)));
//                autores.add(new Autor(
//                                    null,                         
//                                    null, 
//                                    null, 
//                                    literal, 
//                                    a.getDisplayName(), 
//                                    null, 
//                                    null, 
//                                    null, 
//                                    Autor.AutorType.AUT, 
//                                    afiliacionesId, 
//                                    crearIdentificadoresAutor(a)));
//            }
//        }
//        return autores;
//    }
}
