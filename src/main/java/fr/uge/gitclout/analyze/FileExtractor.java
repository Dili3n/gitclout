package fr.uge.gitclout.analyze;

import fr.uge.gitclout.analyze.language.Language;
import org.eclipse.jgit.api.BlameCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.blame.BlameResult;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FileExtractor {
    private final Git repository;
    private final RevCommit commit;
    private ConcurrentHashMap<String, Contributor> contributors = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final ArrayList<Callable<String>> callables = new ArrayList<>();

    public FileExtractor(String tagName, Git repository) throws IOException {
        this.repository = repository;
        this.commit = new RevWalk(repository.getRepository()).parseCommit(repository.getRepository().resolve(tagName));
    }

    /**
     * Adds the contributions of a file to the contributors map.
     * @param blameResult
     * @param file
     */
    private void addContributions(BlameResult blameResult, String file) {
        lock.lock();
        try {
            processBlameResult(blameResult, file);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Processes the blame result of a file.
     * @param blameResult
     * @param file
     */
    private void processBlameResult(BlameResult blameResult, String file) {
        if (blameResult == null) return;
        String language = isLanguageCompatible(file, getType(file));
        if (!language.equals("invalid")) {
            processLinesInBlameResult(blameResult, Language.valueOf(language.toUpperCase()));
        }
    }

    /**
     * Processes the lines of a blame result.
     * @param blameResult
     * @param commentRegex
     */
    private void processLinesInBlameResult(BlameResult blameResult, Language commentRegex) {
        boolean inMultilineComment = false;
        for (int i = 0; i < blameResult.getResultContents().size(); i++) {
            inMultilineComment = processLine(blameResult.getResultContents().getString(i),
                    blameResult.getSourceAuthor(i).getName(),
                    commentRegex,
                    inMultilineComment);
            if (commentRegex.isImage()) return;
        }
    }

    /**
     * Processes a line of code and updates the contributions of a contributor.
     * @param content
     * @param contributorName
     * @param commentRegex
     * @param inMultilineComment
     * @return
     */
    private boolean processLine(String content, String contributorName, Language commentRegex, boolean inMultilineComment) {
        boolean alreadyInMultilineComment = false;
        Contributor contributor = contributors.computeIfAbsent(contributorName, key -> new Contributor(contributorName));
        if (commentRegex.getRegex() != null) {
            if (content.contains(commentRegex.getRegex().getRegexStart()) && !inMultilineComment) {
                    inMultilineComment = true;
                    alreadyInMultilineComment = true;
                }
            updateContributorContributions(contributor, determineLineType(content, commentRegex, inMultilineComment));
            if (content.contains(commentRegex.getRegex().getRegexEnd()) && !alreadyInMultilineComment) inMultilineComment = false;
        } else updateContributorContributions(contributor, commentRegex.getDisplayName());
        return inMultilineComment;
    }

    /**
     * Determines the type of a line of code.
     * @param content
     * @param commentRegex
     * @param inMultilineComment
     * @return
     */
    private static String determineLineType(String content, Language commentRegex, boolean inMultilineComment) {
        Pattern pattern = Pattern.compile(commentRegex.getRegex().getRegex());
        Matcher matcher = pattern.matcher(content);
        boolean match = matcher.find();
        if (!commentRegex.getRegex().getRegexStart().isEmpty() && !commentRegex.getRegex().getRegexEnd().isEmpty()) {
            if (inMultilineComment || match) {
                return "comments";
            } else return commentRegex.getDisplayName();
        } else return commentRegex.getDisplayName();
    }

    /**
     * Updates the contributions of a contributor.
     * @param contributor
     * @param lineType
     */
    private static void updateContributorContributions(Contributor contributor, String lineType) {
        contributor.getContributions().put(lineType, contributor.getContributions().getOrDefault(lineType, 0) + 1);
    }

    /**
     * Returns the supported extensions.
     * @return
     */
    private Set<String> getSupportedExtensions() {
        return Arrays.stream(Language.values())
                .map(Language::getName)
                .collect(Collectors.toSet());
    }

    /**
     * Returns all the files of a commit.
     * @return
     */
    private List<String> getAllFiles() {
        Set<String> supportedExtensions = getSupportedExtensions();
        List<String> files = new ArrayList<>();
        try {
            RevTree tree = commit.getTree();
            processTree(tree, supportedExtensions, files);
        } catch (IOException e) {
            throw new RuntimeException("Error: Getting all files", e);
        }
        return files;
    }

    /**
     * Processes the tree of a commit.
     * @param tree
     * @param supportedExtensions
     * @param files
     * @throws IOException
     */
    private void processTree(RevTree tree, Set<String> supportedExtensions, List<String> files) throws IOException {
        try (TreeWalk treeWalk = new TreeWalk(repository.getRepository())) {
            treeWalk.addTree(tree);
            treeWalk.setRecursive(true);
            while (treeWalk.next()) {
                String path = treeWalk.getPathString();
                if (isSupportedFile(path, supportedExtensions)) {
                    files.add(path);
                }
            }
        }
    }

    /**
     * Checks if a file is supported.
     * @param path
     * @param supportedExtensions
     * @return
     */
    private boolean isSupportedFile(String path, Set<String> supportedExtensions) {
        int lastDotIndex = path.lastIndexOf('.');
        if (lastDotIndex != -1 && lastDotIndex < path.length() - 1) {
            String extension = path.substring(lastDotIndex + 1);
            return supportedExtensions.contains(extension);
        }
        return false;
    }


    /**
     * Analyzes all the contributors of a commit.
     * @return
     * @throws InterruptedException
     */
    public Map<String, Contributor> analyzeAllContributors() throws InterruptedException {
        List<String> files = getAllFiles();
        contributors = new ConcurrentHashMap<>();
        ExecutorService executor = initializeExecutor();
        submitAnalysisTasks(files);
        waitForCompletion(executor);
        repository.close();
        return contributors;
    }

    /**
     * Initializes the executor.
     * @return
     */
    private ExecutorService initializeExecutor() {
        return Executors.newFixedThreadPool(12);
    }

    /**
     * Submits the analysis tasks.
     * @param files
     * @throws InterruptedException
     */
    private void submitAnalysisTasks(List<String> files) {
        for (String file : files) {
            callables.add(() -> analyzeSingleFile(file));
        }
    }

    /**
     * Waits for the completion of the executor.
     * @param executor
     */
    private void waitForCompletion(ExecutorService executor) throws InterruptedException {
        processFutures(executor);
        shutdownAndAwaitTermination(executor);
    }

    /**
     * Processes the futures of the executor.
     * @param executor
     * @throws InterruptedException
     */
    private void processFutures(ExecutorService executor) throws InterruptedException {
        for (Future<String> future : executor.invokeAll(callables)) {
            try {
                future.get();
            } catch (ExecutionException e) {
                throw new RuntimeException("Error: Submitting analysis tasks", e);
            }
        }
    }

    /**
     * Shuts down and awaits termination of the executor.
     * @param executor
     */
    private void shutdownAndAwaitTermination(ExecutorService executor) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.MINUTES)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException ex) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Analyzes a single file.
     * @param file
     * @return
     * @throws GitAPIException
     */
    private String analyzeSingleFile(String file) throws GitAPIException {
        BlameResult blameResult = createBlameCommand().setFilePath(file).call();
        addContributions(blameResult, file);
        return file;
    }

    /**
     * Creates a blame command.
     * @return
     */
    private BlameCommand createBlameCommand() {
        return new BlameCommand(repository.getRepository()).setStartCommit(commit.getId());
    }

    /**
     * Returns the comment regex of a file.
     * @param type
     * @return
     */
    private static Language getCommentRegex(String type) {
        for (var regex : Language.values()) {
            if (regex.getName().equals(type)) {
                return regex;
            }
        }
        return null;
    }

    /**
     * Checks if a file is compatible with a language.
     * @param file
     * @param type
     * @return
     */
    private static String isLanguageCompatible(String file, String type) {
        if (type.equals("xml") && !file.contains("pom.xml")) {
            return "invalid";
        }
        Language regex = getCommentRegex(type);
        return (regex != null) ? regex.getName() : "invalid";
    }

    /**
     * Returns the type of a file.
     * @param file
     * @return
     */
    private static String getType(String file) {
        int lastDotIndex = file.lastIndexOf('.');
        return (lastDotIndex != -1 && lastDotIndex < file.length() - 1) ? file.substring(lastDotIndex + 1) : "";
    }
}
