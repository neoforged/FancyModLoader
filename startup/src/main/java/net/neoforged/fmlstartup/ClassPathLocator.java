/*
 * Copyright (c) NeoForged and contributors
 * SPDX-License-Identifier: LGPL-2.1-only
 */

package net.neoforged.fmlstartup;

//class ClassPathLocator {
//    //private static final String VIRTUAL_JAR_MANIFEST_PATH = "build/virtualJarManifest.properties";
//
//    //final Set<File> searchedDirectories = new HashSet<>();
//    //private final Map<File, VirtualJarManifestEntry> virtualJarMemberIndex = new HashMap<>();
//    //private boolean manifestLoaded;
////
//    //record VirtualJarManifestEntry(String name, List<File> files) {
//    //}
//
//    public void findCandidates(List<FileCacheKey> discovered) throws IOException {
//
//        // Try to find the groupings based on CWD
//        attemptToFindManifest(new File(".").getAbsoluteFile());
//
////        var groupedEntries = new IdentityHashMap<VirtualJarManifestEntry, List<File>>(virtualJarMemberIndex.size());
////
////        for (File file : unclaimed) {
////            var virtualJarSpec = virtualJarMemberIndex.get(file);
////            if (virtualJarSpec != null) {
////                var contentList = groupedEntries.get(virtualJarSpec);
////                if (contentList == null) {
////                    groupedEntries.put(virtualJarSpec, contentList = new ArrayList<>());
////                }
////                contentList.add(file);
////            } else {
////                discovered.add();
////            }
////        }
////
////        for (var entry : groupedEntries.entrySet()) {
////            var locations = entry.getValue();
////            discovered.add(FileContainer.of(locations));
////        }
//    }
//
//    public void attemptToFindManifest(File file) throws IOException {
//        if (manifestLoaded) {
//            return;
//        }
//
//        if (file.isDirectory() && searchedDirectories.add(file)) {
//            var groupingsFile = new File(file, VIRTUAL_JAR_MANIFEST_PATH);
//
//            if (groupingsFile.exists() && loadVirtualJarManifest(groupingsFile)) {
//                return;
//            }
//        }
//
//        var parent = file.getParentFile();
//        if (parent != null) {
//            attemptToFindManifest(parent);
//        }
//    }
//
//    private boolean loadVirtualJarManifest(File manifestFile) throws IOException {
//        Properties p = new Properties();
//        try (var input = new BufferedReader(new InputStreamReader(new FileInputStream(manifestFile)))) {
//            p.load(input);
//        } catch (FileNotFoundException ignored) {
//            return false;
//        }
//
//        StartupLog.info("Loading Virtual Jar manifest from {}", manifestFile);
//
//        for (var virtualJarId : p.stringPropertyNames()) {
//            var paths = p.getProperty(virtualJarId).split(File.pathSeparator);
//            var files = new ArrayList<File>(paths.length);
//            for (String path : paths) {
//                files.add(new File(path));
//            }
//
//            var entry = new VirtualJarManifestEntry(virtualJarId, files);
//            for (var containedFile : files) {
//                virtualJarMemberIndex.put(containedFile, entry);
//            }
//        }
//        manifestLoaded = true;
//        return true;
//    }
//
//    @Override
//    public String toString() {
//        return "classpath locator";
//    }
//}
