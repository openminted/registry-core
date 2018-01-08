package eu.openminted.registry.core.service;

import eu.openminted.registry.core.domain.Resource;
import eu.openminted.registry.core.domain.ResourceType;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;


@Service("restoreService")
public class RestoreServiceImpl implements RestoreService {


    @Autowired
    ResourceTypeService resourceTypeService;

    @Autowired
    ResourceService resourceService;

    @Autowired
    public ParserService parserPool;

    @Override
    public void restoreDataFromZip(MultipartFile file) {
        /**
         * save file to temp
         */
        try {
            File zip = File.createTempFile(UUID.randomUUID().toString(), "temp");
            FileOutputStream o = new FileOutputStream(zip);
            IOUtils.copy(file.getInputStream(), o);
            o.close();

            UnzipUtility unzipUtility = new UnzipUtility();
            Path tempDirPath = Files.createTempDirectory("decompress");
            File tempDirFile = tempDirPath.toFile();
//            tempDir.mkdir();

            unzipUtility.unzip(zip.getAbsolutePath(),tempDirPath.toString());

            List<Resource> resources = new ArrayList<Resource>();
            storeResouces(tempDirFile, resources);

            zip.deleteOnExit();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    private void storeResouces(File dir, List<Resource> resources) {
        File[] files = dir.listFiles();

        for (File file : files) {
            if(file.isDirectory()) {
                storeResouces(file, resources);
            }else {
                if (FilenameUtils.removeExtension(file.getName()).equals(file.getParentFile().getName())) {
                    //if there is a file with the same name as the directory then it's the schema of the resource type. Drop resource type and reimport
                    String resourceTypeName = file.getParentFile().getName();
                    System.out.println("Adding resource type:"+resourceTypeName);
                    if(resourceTypeService.getResourceType(resourceTypeName)!=null)
                        resourceTypeService.deleteResourceType(resourceTypeName);

                    ResourceType resourceType = new ResourceType();
                    try {
                        resourceType = parserPool.deserialize(FileUtils.readFileToString(file).replaceAll("^\t$", "").replaceAll("^\n$",""),ResourceType.class);
                        resourceTypeService.addResourceType(resourceType);
                    } catch (IOException e) {
                        new ServiceException("Failed to read schema file");
                    }


                }
            }
        }

        for(File file : files){
            try {
                if(!file.isDirectory()) {
                    String[] splitInto = file.getAbsolutePath().split("/");


                    ResourceType resourceType = resourceTypeService.getResourceType(splitInto[splitInto.length - 1]);
                    if(!FilenameUtils.removeExtension(file.getName()).equals(file.getParentFile().getName())){
                        //if it's not the schema file then add it as a resource
                        System.out.println("Adding resource:"+file.getName());
                        String extension = FilenameUtils.getExtension(file.getName());
                        Resource resource = new Resource();
                        if(extension.equals("json")) {
                            resource = parserPool.deserializeResource(file, ParserService.ParserServiceTypes.JSON);
                            if(resource==null)
                                resource = parserPool.deserializeResource(file, ParserService.ParserServiceTypes.XML);
                        }else if(extension.equals("xml")){
                            resource = parserPool.deserializeResource(file, ParserService.ParserServiceTypes.XML);
                            if(resource==null)
                                resource = parserPool.deserializeResource(file, ParserService.ParserServiceTypes.JSON);
                        }else{
                            new ServiceException("Unsupported file format");
                        }

                        if(resource==null) {//if it's still null that means that the file contains just the payload
                            resource = new Resource();
                            resource.setPayload(FileUtils.readFileToString(file));
                            resource.setPayloadFormat(extension);
                            resource.setResourceType(file.getParentFile().getName());
                            resourceService.addResource(resource);
                        }else{
                            resourceService.addResource(resource);
                        }
                    }


                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    public class UnzipUtility {
        /**
         * Size of the buffer to read/write data
         */
        private static final int BUFFER_SIZE = 4096;
        /**
         * Extracts a zip file specified by the zipFilePath to a directory specified by
         * destDirectory (will be created if does not exists)
         * @param zipFilePath
         * @param destDirectory
         * @throws IOException
         */
        public void unzip(String zipFilePath, String destDirectory) throws IOException {
            File destDir = new File(destDirectory);
            if (!destDir.exists()) {
                destDir.mkdir();
            }
            ZipInputStream zipIn = new ZipInputStream(new FileInputStream(zipFilePath));
            ZipEntry entry = zipIn.getNextEntry();
            // iterates over entries in the zip file
            while (entry != null) {
                String filePath = destDirectory + File.separator + entry.getName();
//                System.out.println(entry.getName());


                String[] splitInto = entry.getName().split("/");
                File tmpFile = new File(destDirectory+File.separator+splitInto[splitInto.length-2]);
                if(!tmpFile.exists())
                    tmpFile.mkdir();

                extractFile(zipIn, filePath);

                zipIn.closeEntry();
                entry = zipIn.getNextEntry();
            }
            zipIn.close();
        }
        /**
         * Extracts a zip entry (file entry)
         * @param zipIn
         * @param filePath
         * @throws IOException
         */
        private void extractFile(ZipInputStream zipIn, String filePath) throws IOException {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePath));
            byte[] bytesIn = new byte[BUFFER_SIZE];
            int read = 0;
            while ((read = zipIn.read(bytesIn)) != -1) {
                bos.write(bytesIn, 0, read);
            }
            bos.close();
        }


    }
}