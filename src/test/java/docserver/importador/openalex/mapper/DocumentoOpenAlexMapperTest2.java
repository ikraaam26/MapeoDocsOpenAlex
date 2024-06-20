/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package docserver.importador.openalex.mapper;

import dialnet.docserver.model.documentos.DocumentoRepository;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import openalex.documentos.model.DocumentoOpenalexRepository;
import openalex.documentos.model.Version;
import openalex.documentos.model.VersionesRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 *
 * @author practicas
 */
@Slf4j
@SpringBootTest
public class DocumentoOpenAlexMapperTest2 {
    
    @Autowired
    private VersionesRepository versionesRepository;
    @Autowired
    private DocumentoOpenalexRepository docRepository;
    
    //@Test
    public void testFindAll() {
        
        Optional<Version> v= versionesRepository.findById("5bbc6930b750603269e816cd");
        if(v.isPresent()){
            Version v2= v.get();
            System.out.println("id: " + v2.getId());
        }
        
    }
    
    @Test
    public void test(){
        
        Assertions.assertTrue(versionesRepository.existsByIdentificador("DIALNET_ART", "2419"));
        Assertions.assertFalse(versionesRepository.existsByIdentificador("DIALNET_ART", "x"));
               
    }
}
