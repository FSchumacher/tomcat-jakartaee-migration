/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tomcat.jakartaee;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class MigrationTest {

    private boolean securityManagerAvailable = true;

    @Before
    public void setUp() {
        try {
            System.setSecurityManager(new NoExitSecurityManager());
        } catch (Throwable t) {
            // Throws exception by default on newer Java versions
            securityManagerAvailable = false;
        }
    }

    @After
    public void tearDown() {
        try {
            System.setSecurityManager(null);
        } catch (Throwable t) {
            // Throws exception by default on newer Java versions
        }
    }

    @Test
    public void testMigrateSingleSourceFile() throws Exception {
        File migratedFile = new File("target/test-classes/HelloServlet.migrated.java");
        MigrationCLI.main(new String[] {"target/test-classes/HelloServlet.java", migratedFile.getAbsolutePath()});

        assertTrue("Migrated file not found", migratedFile.exists());

        String migratedSource = FileUtils.readFileToString(migratedFile, StandardCharsets.UTF_8);
        assertFalse("Imports not migrated", migratedSource.contains("import javax.servlet"));
        assertTrue("Migrated imports not found", migratedSource.contains("import jakarta.servlet"));
    }

    @Test
    public void testMigrateSingleSourceFileWithProfile() throws Exception {
        File migratedFile = new File("target/test-classes/HelloServlet.migrated.java");
        MigrationCLI.main(new String[] {"-logLevel=FINE", "-profile=EE", "target/test-classes/HelloServlet.java", migratedFile.getAbsolutePath()});

        assertTrue("Migrated file not found", migratedFile.exists());

        String migratedSource = FileUtils.readFileToString(migratedFile, StandardCharsets.UTF_8);
        assertFalse("Imports not migrated", migratedSource.contains("import javax.servlet"));
        assertTrue("Migrated imports not found", migratedSource.contains("import jakarta.servlet"));
    }

    @Test
    public void testMigrateSingleSourceFileInPlace() throws Exception {
        File sourceFile = new File("target/test-classes/HelloServlet.java");
        File migratedFile = new File("target/test-classes/HelloServlet.inplace.java");
        FileUtils.copyFile(sourceFile, migratedFile);

        MigrationCLI.main(new String[] {"-profile=EE", migratedFile.getAbsolutePath(), migratedFile.getAbsolutePath()});

        assertTrue("Migrated file not found", migratedFile.exists());

        String migratedSource = FileUtils.readFileToString(migratedFile, StandardCharsets.UTF_8);
        assertFalse("Imports not migrated", migratedSource.contains("import javax.servlet"));
        assertTrue("Migrated imports not found", migratedSource.contains("import jakarta.servlet"));
    }

    @Test
    public void testInvalidOption() throws Exception {
        Assume.assumeTrue(securityManagerAvailable);
        File sourceFile = new File("target/test-classes/HelloServlet.java");
        File migratedFile = new File("target/test-classes/HelloServlet.migrated.java");

        try {
            MigrationCLI.main(new String[] {"-invalid", sourceFile.getAbsolutePath(), migratedFile.getAbsolutePath()});
            fail("No error code returned");
        } catch (SecurityException e) {
            assertEquals("error code", "1", e.getMessage());
        }
    }

    @Test
    public void testInvalidProfile() throws Exception {
        Assume.assumeTrue(securityManagerAvailable);
        File sourceFile = new File("target/test-classes/HelloServlet.java");
        File migratedFile = new File("target/test-classes/HelloServlet.migrated.java");

        try {
            MigrationCLI.main(new String[] {"-profile=JSERV", sourceFile.getAbsolutePath(), migratedFile.getAbsolutePath()});
            fail("No error code returned");
        } catch (SecurityException e) {
            assertEquals("error code", "1", e.getMessage());
        }
    }

    @Test
    public void testMigrateDirectory() throws Exception {
        File sourceDirectory = new File("src/test/resources");
        File destinationDirectory = new File("target/test-classes/migration");

        Migration migration = new Migration();
        migration.setSource(sourceDirectory);
        migration.setDestination(destinationDirectory);
        migration.execute();

        assertTrue("Destination directory not found", destinationDirectory.exists());

        File migratedFile = new File("target/test-classes/migration/HelloServlet.java");
        assertTrue("Migrated file not found", migratedFile.exists());

        String migratedSource = FileUtils.readFileToString(migratedFile, StandardCharsets.UTF_8);
        assertFalse("Imports not migrated", migratedSource.contains("import javax.servlet"));
        assertTrue("Migrated imports not found", migratedSource.contains("import jakarta.servlet"));

        File migratedSpiFile = new File("target/test-classes/migration/javax.enterprise.inject.spi.Extension");
        assertTrue("SPI file has not been migrated by renaming", migratedSpiFile.exists());

        String migratedSpiSource = FileUtils.readFileToString(migratedSpiFile, StandardCharsets.UTF_8);
        assertTrue("SPI file copied with content", migratedSpiSource.contains("some.class.Reference"));
    }

    @Test
    public void testMigrateDirectoryWithEeProfile() throws Exception {
        File sourceDirectory = new File("src/test/resources");
        File destinationDirectory = new File("target/test-classes/migration-ee");

        Migration migration = new Migration();
        migration.setEESpecProfile(EESpecProfiles.EE);
        migration.setSource(sourceDirectory);
        migration.setDestination(destinationDirectory);
        migration.execute();

        assertTrue("Destination directory not found", destinationDirectory.exists());

        File migratedFile = new File(destinationDirectory, "HelloServlet.java");
        assertTrue("Migrated file not found", migratedFile.exists());

        String migratedSource = FileUtils.readFileToString(migratedFile, StandardCharsets.UTF_8);
        assertFalse("Imports not migrated", migratedSource.contains("import javax.servlet"));
        assertTrue("Migrated imports not found", migratedSource.contains("import jakarta.servlet"));

        File migratedSpiFile = new File(destinationDirectory, "jakarta.enterprise.inject.spi.Extension");
        assertTrue("SPI file migrated by renaming", migratedSpiFile.exists());

        String migratedSpiSource = FileUtils.readFileToString(migratedSpiFile, StandardCharsets.UTF_8);
        assertTrue("SPI file copied with content", migratedSpiSource.contains("some.class.Reference"));
    }

    @Test
    public void testMigrateClassFile() throws Exception {
        File classFile = new File("target/test-classes/org/apache/tomcat/jakartaee/HelloCGI.class");

        Migration migration = new Migration();
        migration.setSource(classFile);
        migration.setDestination(classFile);
        migration.execute();

        Class<?> cls = Class.forName("org.apache.tomcat.jakartaee.HelloCGI");
        assertEquals("jakarta.servlet.CommonGatewayInterface", cls.getSuperclass().getName());
    }

    @Test
    public void testMigrateJarFile() throws Exception {
        testMigrateJarFileInternal(false);
    }

    private void testMigrateJarFileInternal(boolean zipInMemory) throws Exception {
        File jarFile = new File("target/test-classes/hellocgi.jar");
        File jarFileTarget = new File("target/test-classes/hellocgi-target.jar");

        Migration migration = new Migration();
        migration.setSource(jarFile);
        migration.setDestination(jarFileTarget);
        migration.setZipInMemory(zipInMemory);
        migration.execute();

        File cgiapiFile = new File("target/test-classes/cgi-api.jar");
        URLClassLoader classloader = new URLClassLoader(new URL[]{jarFileTarget.toURI().toURL(), cgiapiFile.toURI().toURL()},ClassLoader.getSystemClassLoader().getParent());

        Class<?> cls = Class.forName("org.apache.tomcat.jakartaee.HelloCGI", true, classloader);
        assertEquals("jakarta.servlet.CommonGatewayInterface", cls.getSuperclass().getName());

        // check the modification of the Implementation-Version manifest attribute
        try (JarFile jar = new JarFile(jarFileTarget)) {
            String implementationVersion = jar.getManifest().getMainAttributes().getValue("Implementation-Version");
            assertNotNull("Missing Implementation-Version manifest attribute", implementationVersion);
            assertNotEquals("Implementation-Version manifest attribute not changed", "1.2.3", implementationVersion);
            assertTrue("Implementation-Version manifest attribute doesn't match the expected pattern", implementationVersion.matches("1\\.2\\.3-migrated-[\\d\\.]+.*"));
        }

        assertTrue("hasConverted should be true", migration.hasConverted());
    }

    @Test
    public void testMigrateJarFileInMemory() throws Exception {
        testMigrateJarFileInternal(true);
    }

    @Test
    public void testHasConversionsThrowsWhenNotComplete() {
        Migration migration = new Migration();
        IllegalStateException exception = assertThrows(IllegalStateException.class, migration::hasConverted);
        assertEquals("Migration has not completed", exception.getMessage());
    }

    @Test
    public void testMigrateSignedJarFileRSA() throws Exception {
        testMigrateSignedJarFile("rsa");
    }

    @Test
    public void testMigrateSignedJarFileDSA() throws Exception {
        testMigrateSignedJarFile("dsa");
    }

    @Test
    public void testMigrateSignedJarFileEC() throws Exception {
        testMigrateSignedJarFile("ec");
    }

    private void testMigrateSignedJarFile(String algorithm) throws Exception {
        File jarFile = new File("target/test-classes/hellocgi-signed-" + algorithm + ".jar");

        Migration migration = new Migration();
        migration.setSource(jarFile);
        migration.setDestination(jarFile);
        migration.execute();

        try (JarFile jar = new JarFile(jarFile)) {
            assertNull("Digest not removed from the manifest", jar.getManifest().getAttributes("org/apache/tomcat/jakartaee/HelloCGI.class"));
            assertNull("Signature key not removed", jar.getEntry("META-INF/" + algorithm.toUpperCase() + "." + algorithm.toUpperCase()));
            assertNull("Signed manifest not removed", jar.getEntry("META-INF/" + algorithm.toUpperCase() + ".SF"));
        }
    }
}
