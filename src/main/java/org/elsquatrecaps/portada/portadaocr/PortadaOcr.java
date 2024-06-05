/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Project/Maven2/JavaApp/src/main/java/${packagePath}/${mainClassName}.java to edit this template
 */

package org.elsquatrecaps.portada.portadaocr;

import com.google.cloud.documentai.v1beta3.Document;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author josepcanellas
 */
public class PortadaOcr {

    public static void main(String[] args) {
        String path = "../data/imatgeTransparencia.jpg";
        ProcessOcrDocument processOcrDocument = new ProcessOcrDocument();
        File ocrDir = new File("../ocr");
        if(!ocrDir.exists()){
            ocrDir.mkdirs();
            //ocdDir.mkdir();
        }
        try {
            processOcrDocument.init(new File("./").getCanonicalFile().getAbsolutePath(), "bcn");
            processOcrDocument.process(path);
            Document dc = processOcrDocument.getResult();
            processOcrDocument.saveText("../ocr/imatgeTransparencia.txt");
            processOcrDocument.saveRawText("../ocr/imatgeTransparencia.or.txt");
            System.out.println(dc.getText());
            
            processOcrDocument.process("../data/inclinada.jpg");
            dc = processOcrDocument.getResult();
            processOcrDocument.saveText("../ocr/inclinada.txt");
            processOcrDocument.saveRawText("../ocr/inclinada.or.txt");
            System.out.println(dc.getText());
            

        } catch (IOException ex) {
            Logger.getLogger(PortadaOcr.class.getName()).log(Level.SEVERE, null, ex);
        }
        
    }
}
