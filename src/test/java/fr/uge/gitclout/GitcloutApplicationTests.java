package fr.uge.gitclout;

import fr.uge.gitclout.analyze.Contributor;
import fr.uge.gitclout.analyze.FileExtractor;
import fr.uge.gitclout.analyze.Refresh;
import fr.uge.gitclout.analyze.language.Language;
import fr.uge.gitclout.analyze.api.data.ContributorData;
import fr.uge.gitclout.analyze.jpa.ContributorRequest;
import fr.uge.gitclout.analyze.jpa.ContributorStorage;
import fr.uge.gitclout.analyze.language.Regex;
import fr.uge.gitclout.repositories.api.data.HistoryData;
import fr.uge.gitclout.repositories.api.data.RepositoryData;
import fr.uge.gitclout.repositories.jpa.RepositoryRequest;
import fr.uge.gitclout.repositories.services.RepositoryService;
import fr.uge.gitclout.tags.api.SseController;
import fr.uge.gitclout.tags.api.data.RefreshData;
import fr.uge.gitclout.tags.api.data.TagData;
import fr.uge.gitclout.tags.jpa.TagRequest;
import fr.uge.gitclout.tags.services.TagService;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(classes = GitcloutApplication.class)
class GitcloutApplicationTests {

	private static RepositoryService repositoryService;

	private final String url = "https://github.com/bruno00o/test-gitclout.git";

	private static Mono<RepositoryData> repositoryTmp;

	private static TagService tagService;

	@BeforeAll
	static void setUpAll() {
		TagRequest tagRequest = Mockito.mock(TagRequest.class);
		ContributorRequest contributorRequest = Mockito.mock(ContributorRequest.class);
		SseController sseController = Mockito.mock(SseController.class);
		RepositoryRequest repositoryRequest = Mockito.mock(RepositoryRequest.class);
		repositoryService = new RepositoryService(repositoryRequest, tagRequest, sseController, contributorRequest);
		repositoryTmp = repositoryService.addRepository("https://github.com/bruno00o/test-gitclout.git");
		tagService = new TagService(tagRequest, contributorRequest, sseController);
	}

	@AfterAll
	static void tearDown() {
		repositoryService.deleteRepository(Objects.requireNonNull(repositoryTmp.block()).repositoryName());
	}

	@Nested
	class RepositoryServiceOperations {

		private String projectName;

		@Test
		@Order(1)
		public void checkUrlTest() {
			assertTrue(RepositoryService.checkUrl(url));
			assertFalse(RepositoryService.checkUrl("pas bon url"));
		}

		@Test
		@Order(2)
		public void addRepositoryTest() {
			projectName = Objects.requireNonNull(repositoryTmp.block()).repositoryName().split("_")[0];
			assertEquals("test-gitclout.git", projectName);
			assertEquals(0, Objects.requireNonNull(repositoryTmp.block()).error());
			assertEquals("Your repository has been added", Objects.requireNonNull(repositoryTmp.block()).message());
		}

		@Test
		@Order(3)
		public void repositoryHistoryTest() {
			Flux<HistoryData> data = repositoryService.repositoryHistory();
			for (HistoryData historyData : data.toIterable()) {
				if (historyData.name().equals("test-gitclout")) {
					assertEquals("test-gitclout", historyData.name());
				}
			}
		}

		@Test
		@Order(4)
		public void refreshRepositoryTest() {
			var repositoryData = repositoryService.addRepository(url);
			Mono<RefreshData> refreshData = repositoryService.refreshTags(Objects.requireNonNull(repositoryData.block()).repositoryName());
			assertEquals(0, Objects.requireNonNull(refreshData.block()).error());
			assertEquals("Tags refreshed", Objects.requireNonNull(refreshData.block()).message());
			repositoryService.deleteRepository(Objects.requireNonNull(repositoryData.block()).repositoryName());
		}

		@Test
		public void isRepositoryExistTest() throws InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(RepositoryService.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("isRepositoryExist"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertFalse((boolean) privateMethod.invoke(repositoryService));
		}

		@Test
		public void getDisplayNameTest() throws InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(RepositoryService.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("getDisplayName"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertEquals("test-gitclout.git", privateMethod.invoke(repositoryService, Objects.requireNonNull(repositoryTmp.block()).repositoryName()));
		}

		@Test
		public void getRepositoryNameTest() throws InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(RepositoryService.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("getRepositoryName"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd-HHmmss");
			assertEquals("test-gitclout.git_" + dateFormat.format(new Date()), privateMethod.invoke(repositoryService, "test-gitclout.git"));
		}



		@Test
		public void removeAllTagsTest() {
			var privateMethod = Arrays.stream(RepositoryService.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("removeAllTags"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertDoesNotThrow(() -> privateMethod.invoke(repositoryService, projectName));
		}

		@Test
		public void removeContributorsTest() {
			var privateMethod = Arrays.stream(RepositoryService.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("removeContributors"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertDoesNotThrow(() -> privateMethod.invoke(repositoryService, projectName));
		}
	}

	@Nested
	class TagServiceOperations {

		@Test
		public void getTagNameTest() {
			Flux<TagData> tags = tagService.getTags("test-gitclout");
			StepVerifier.create(tags)
					.expectNextMatches(tagData -> {
						assertEquals("v1.0.0", tagData.name());
						return true;
					});
		}

		@Test
		public void collectContributorsStorageTest() throws IOException, GitAPIException, InterruptedException {
			List<ContributorStorage> realContributor = new ArrayList<>();
			realContributor.add(new ContributorStorage("Bruno", "python", "v1.0.0", 7, Objects.requireNonNull(repositoryTmp.block()).repositoryName()));
			Git git = Git.open(Objects.requireNonNull(TagService.getRepositoryPath(Objects.requireNonNull(repositoryTmp.block()).repositoryName())).toFile());
			var tag = git.tagList().call().get(0);
			var filesExtractor = new FileExtractor(TagService.getTagName(tag), git);
			var contributors = filesExtractor.analyzeAllContributors();
			List<ContributorStorage> contributorStorages = TagService.collectContributorStorages(contributors, TagService.getTagName(tag), Objects.requireNonNull(repositoryTmp.block()).repositoryName());
			assertEquals(realContributor.get(0).contributorName(), contributorStorages.get(0).contributorName());
		}

		@Test
		public void insertTagsInDatabaseTest() {
			assertThrows(RuntimeException.class, () -> tagService.insertTagsInDatabase("no"));
		}

		@Test
		public void getRepositoryPathTest() {
			String tempDir = System.getProperty("java.io.tmpdir");
			assertEquals(Paths.get(tempDir, "gitclout_tmp", Objects.requireNonNull(repositoryTmp.block()).repositoryName()).toString(), Objects.requireNonNull(TagService.getRepositoryPath(Objects.requireNonNull(repositoryTmp.block()).repositoryName())).toString());
		}

		@Test
		public void closeGitTest() {
			var privateMethod = Arrays.stream(TagService.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("closeGit"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertDoesNotThrow(() -> privateMethod.invoke(tagService));
		}

		@Test
		public void setTagAnalyzedTest() {
			var privateMethod = Arrays.stream(TagService.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("setTagAnalyzed"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertDoesNotThrow(() -> privateMethod.invoke(tagService, "test-gitclout"));
		}

		@Test
		public void isTagExistTest() throws InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(TagService.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("isTagExist"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertFalse((boolean) privateMethod.invoke(tagService, "no", "refs/tags/v9.4.5"));
		}

		@Test
		public void getContributorsTest() {
			Flux<ContributorData> contributors = tagService.getContributors("test-gitclout.git", "v1.0.0");
			StepVerifier.create(contributors)
					.expectNextMatches(contributorData -> {
						assertEquals("Bruno", contributorData.name());
						return true;
					});
		}

		@Test
		public void getTagsTest() {
			Flux<TagData> tags = tagService.getTags("test-gitclout.git");
			StepVerifier.create(tags)
					.expectNextMatches(tagData -> {
						assertEquals("v1.0.0", tagData.name());
						return true;
					});
		}
	}

	@Nested
	class FileExtractorOperations {

		private static FileExtractor fileExtractor;

		@BeforeAll
		static void setUp() throws IOException {
			fileExtractor = new FileExtractor("v1.0.0", Git.open(Objects.requireNonNull(TagService.getRepositoryPath(Objects.requireNonNull(repositoryTmp.block()).repositoryName())).toFile()));
		}

		@Test
		public void analyzeAllContributorsTest() throws IOException, GitAPIException, InterruptedException {
			Git git = Git.open(Objects.requireNonNull(TagService.getRepositoryPath(Objects.requireNonNull(repositoryTmp.block()).repositoryName())).toFile());
			var tag = git.tagList().call().get(0);
			var filesExtractor = new FileExtractor(TagService.getTagName(tag), git);
			Map<String, Contributor> contributors = filesExtractor.analyzeAllContributors();
			assertEquals("Bruno", contributors.get("Bruno").name());
			assertEquals(7, contributors.get("Bruno").contributions().getOrDefault("python", 0));
			assertEquals(21, contributors.get("Bruno").contributions().getOrDefault("comments", 0));
		}

		@Test
		public void analyzeSingleFileTest() {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("analyzeSingleFile"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertThrows(NullPointerException.class, () -> privateMethod.invoke(new FileExtractor("no", null), "no"));
		}

		@Test
		public void getTypeTest() throws IOException, InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("getType"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertEquals("py", privateMethod.invoke(fileExtractor, "test.py"));
			assertEquals("java", privateMethod.invoke(fileExtractor, "test.java"));
			assertEquals("c", privateMethod.invoke(fileExtractor, "test.c"));
		}

		@Test
		public void isLanguageCompatibleTest() throws IOException, InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("isLanguageCompatible"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertEquals("py", privateMethod.invoke(fileExtractor, "test.py", "py"));
			assertEquals("java", privateMethod.invoke(fileExtractor, "test.java", "java"));
			assertEquals("c", privateMethod.invoke(fileExtractor, "test.c", "c"));
			assertEquals("invalid", privateMethod.invoke(fileExtractor, "test", "eur"));
		}

		@Test
		public void processLineTest() throws IOException, InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("processLine"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertFalse((boolean) privateMethod.invoke(fileExtractor, "test", "test", Language.PY, false));
		}

		@Test
		public void addContributionsTest() throws IOException {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("addContributions"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertDoesNotThrow(() -> privateMethod.invoke(fileExtractor, null, "test"));
		}

		@Test
		public void processLineTest2() throws InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("processLine"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertTrue((boolean) privateMethod.invoke(fileExtractor, "test", "test", Language.PY, true));
		}

		@Test
		public void determineLineTypeTest() throws InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("determineLineType"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertEquals("python", privateMethod.invoke(fileExtractor, "test", Language.PY, false));
			assertEquals("comments", privateMethod.invoke(fileExtractor, "test", Language.PY, true));
			assertEquals("java", privateMethod.invoke(fileExtractor, "test", Language.JAVA, false));
			assertEquals("comments", privateMethod.invoke(fileExtractor, "test", Language.JAVA, true));
			assertEquals("c", privateMethod.invoke(fileExtractor, "test", Language.C, false));
		}

		@Test
		public void updateContributorContributionsTest() {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("updateContributorContributions"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			Contributor contributor = new Contributor("Bruno");
			assertDoesNotThrow(() -> privateMethod.invoke(fileExtractor, contributor, "test"));
		}

		@Test
		public void getCommentRegexTest() throws InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("getCommentRegex"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertEquals(Language.PY, privateMethod.invoke(fileExtractor, "py"));
			assertEquals(Language.JAVA, privateMethod.invoke(fileExtractor, "java"));
			assertEquals(Language.C, privateMethod.invoke(fileExtractor, "c"));
			assertNull(privateMethod.invoke(fileExtractor, "eur"));
		}

		@Test
		public void createBlameCommandTest() {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("createBlameCommand"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertDoesNotThrow(() -> privateMethod.invoke(fileExtractor));
		}

		@Test
		public void isSupportedFileTest() throws InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("isSupportedFile"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			Set<String> supportedExtensions = new HashSet<>();
			supportedExtensions.add("py");
			assertTrue((boolean) privateMethod.invoke(fileExtractor, "test.py", supportedExtensions));
			assertFalse((boolean) privateMethod.invoke(fileExtractor, "test.eur", supportedExtensions));
		}

		@Test
		public void getSupportedExtensionsTest() throws InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(FileExtractor.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("getSupportedExtensions"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			Set<String> supportedExtensions = new HashSet<>();
			supportedExtensions.add("py");
			assertTrue(privateMethod.invoke(fileExtractor).toString().contains("py"), "true");
		}


	}

	@Nested
	class LanguageOperations {

		@Test
		public void isImageTest() {
			assertTrue(Language.JPG.isImage());
			assertTrue(Language.PNG.isImage());
			assertTrue(Language.GIF.isImage());
			assertTrue(Language.SVG.isImage());
			assertFalse(Language.JAVA.isImage());
		}

		@Test
		public void getNameTest() {
			assertEquals("java", Language.JAVA.getName());
			assertEquals("py", Language.PY.getName());
			assertEquals("c", Language.C.getName());
			assertEquals("js", Language.JS.getName());
			assertEquals("rb", Language.RUBY.getName());
			assertEquals("php", Language.PHP.getName());
			assertEquals("css", Language.CSS.getName());
		}

		@Test
		public void getRegexTest() {
			assertEquals(Regex.TYPE_ONE_COMMENT, Language.JAVA.getRegex());
			assertEquals(Regex.TYPE_TWO_COMMENT, Language.PY.getRegex());
			assertEquals(Regex.TYPE_ONE_COMMENT, Language.C.getRegex());
			assertEquals(Regex.TYPE_ONE_COMMENT, Language.JS.getRegex());
			assertEquals(Regex.TYPE_THREE_COMMENT, Language.RUBY.getRegex());
			assertEquals(Regex.TYPE_ONE_COMMENT, Language.PHP.getRegex());
		}
	}

	@Nested
	class RegexOperations {

		@Test
		public void getRegexTest() {
			assertEquals("//.*", Regex.TYPE_ONE_COMMENT.getRegex());
			assertEquals("#.*", Regex.TYPE_TWO_COMMENT.getRegex());
			assertEquals("#.*", Regex.TYPE_THREE_COMMENT.getRegex());
		}
	}

	@Nested
	class RefreshOperations {

		private static Refresh refresh;

		@BeforeAll
		static void setUp() {
			TagRequest tagRequest = Mockito.mock(TagRequest.class);
			ContributorRequest contributorRequest = Mockito.mock(ContributorRequest.class);
			refresh = new Refresh("test-gitclout.git", tagRequest, contributorRequest);
			MockitoAnnotations.openMocks(refresh);
		}

		@Test
		public void isTagExist() throws InvocationTargetException, IllegalAccessException {
			var privateMethod = Arrays.stream(Refresh.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("isTagExist"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertFalse((boolean) privateMethod.invoke(refresh, "no"));
		}

		@Test
		public void refreshTagsTest() {
			assertThrows(RuntimeException.class, () -> refresh.refreshTags());
		}

		@Test
		public void insertATagInDatabaseByRefreshingTest() {
			var privateMethod = Arrays.stream(Refresh.class.getDeclaredMethods())
					.filter(method -> method.getName().equals("insertATagInDatabaseByRefreshing"))
					.findFirst()
					.orElseThrow();
			privateMethod.setAccessible(true);
			assertThrows(RuntimeException.class, () -> privateMethod.invoke(refresh, "no", "no"));
		}

	}

}
