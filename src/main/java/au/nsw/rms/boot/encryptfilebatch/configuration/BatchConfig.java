package au.nsw.rms.boot.encryptfilebatch.configuration;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.FlowBuilder;
import org.springframework.batch.core.job.flow.Flow;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.partition.support.MultiResourcePartitioner;
import org.springframework.batch.core.partition.support.Partitioner;
import org.springframework.batch.core.step.tasklet.SystemCommandTasklet;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileItemWriter;
import org.springframework.batch.item.file.builder.FlatFileItemWriterBuilder;
import org.springframework.batch.item.file.mapping.PassThroughLineMapper;
import org.springframework.batch.item.file.transform.PassThroughLineAggregator;
import org.springframework.batch.item.support.SynchronizedItemStreamReader;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Function;

/**
 * Created by pavan on 10/12/18.
 */
@Configuration
@EnableBatchProcessing
public class BatchConfig {

    private Logger logger = LoggerFactory.getLogger(BatchConfig.class);

    @Autowired
    @Setter
    private JobBuilderFactory jobBuilderFactory;

    @Autowired
    @Setter
    private StepBuilderFactory stepBuilderFactory;

    @Value("${fullFilePath:plain.txt}")
    private String filePath;

    @Value("${threadCount:5}")
    private int threadCount;

    @Value("${cipherKeyShiftValue:3}")
    private int cipherShiftValue;

    private String newFileName;

    private Path encryptedDirectoryPath;

    @Value("${outputFileName:#{null}}")
    private String outputFileName;

    private Path outputFilePath;


    @Bean
    @StepScope
    public SynchronizedItemStreamReader<String> reader(@Value("#{stepExecutionContext['fileName']}") String file) throws IOException {
        logger.info("Input file is - " + file);
        //Delete split file after program exits
        UrlResource urlResource = new UrlResource(file);
        File fileObject = urlResource.getFile();
        if (fileObject.exists())
            fileObject.deleteOnExit();

        FlatFileItemReader<String> reader = new FlatFileItemReader<>();
        SynchronizedItemStreamReader synchronizedItemStreamReader = new SynchronizedItemStreamReader();
        reader.setResource(urlResource);
        reader.setLineMapper(new PassThroughLineMapper());
        synchronizedItemStreamReader.setDelegate(reader);
        return synchronizedItemStreamReader;
    }


    @Bean
    @StepScope
    public Partitioner partitioner() throws Exception {
        MultiResourcePartitioner partitioner = new MultiResourcePartitioner();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        //Filenames split by the command will start with a, so we using a regex.
        Resource[] resources = resolver.getResources(String.format("file://%sa*", newFileName));
        partitioner.setResources(resources);
        return partitioner;
    }

    private Function<String, String> caesarCipher(int shift) {
        return (String plain) -> {
            StringBuilder cipherBuilder = plain.chars()
                    .mapToObj(eachChar -> (char) eachChar)
                    .map(toTransform -> {
                        char toReturn = toTransform;
                        if (Character.isLetter(toTransform)) {
                            toReturn = (char) (toTransform + shift);
                            // if the character exceeds 'z' or 'Z' it will reverse the logic to reduce
                            if ((Character.isLowerCase(toTransform) && toReturn > 'z') || (Character.isUpperCase(toTransform) && toReturn > 'Z')) {
                                toReturn = (char) (toTransform - (26 - shift));
                            }
                        }
                        return toReturn;
                    })
                    .collect(StringBuilder::new, StringBuilder::append, StringBuilder::append);
            logger.info(cipherBuilder.toString());
            return cipherBuilder.toString();
        };
    }

    @Bean
    @StepScope
    public FlatFileItemWriter<String> writeToFiles() {
        return new FlatFileItemWriterBuilder<String>()
                .name("writeToFile")
                .append(true)
                .encoding(StandardCharsets.UTF_8.toString())
                .lineAggregator(new PassThroughLineAggregator<>())
                .resource(new FileSystemResource(outputFilePath))
                .build();
    }

    @Bean
    public Step asynchronousChunkedReadingStep() throws IOException {
        return stepBuilderFactory.get("slaveStep")
                .<String, String>chunk(5)
                .reader(reader(null))
                .processor(caesarCipher(cipherShiftValue))
                .writer(writeToFiles())
                .build();
    }

    @Bean
    public Step splitFileTasklet() throws IOException {

        String path = Paths.get(filePath).normalize().toAbsolutePath().toString();
        File file = Files.createTempDirectory("something").toFile();
        file.deleteOnExit();
        Path tempDirWithPrefix = file.toPath();
        newFileName = tempDirWithPrefix.toAbsolutePath().toString();
        logger.info(String.format("split -a 5 -l 10 %s %s", path, newFileName));
        SystemCommandTasklet systemCommandTasklet = new SystemCommandTasklet();
        systemCommandTasklet.setCommand(String.format("split -a 5 -l 10 %s %s", path, newFileName));
        systemCommandTasklet.setTimeout(60 * 1000);


        return stepBuilderFactory.get("splitFileStep")
                .tasklet(systemCommandTasklet).build();

    }

    @Bean
    public SimpleAsyncTaskExecutor getAsyncTaskExecutor() {
        final SimpleAsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor();
        taskExecutor.setConcurrencyLimit(threadCount);
        return taskExecutor;
    }

    @Bean
    @Qualifier("secondStep")
    public Step secondStep() throws Exception {
        return stepBuilderFactory.get("subsequentReadTransformAndWrite")
                .partitioner(asynchronousChunkedReadingStep())
                .partitioner("partition", partitioner())
                .gridSize(10)
                .taskExecutor(getAsyncTaskExecutor())
                .build();
    }

    @Bean
    public Job parallelStepsJob() throws Exception {

        encryptedDirectoryPath = Paths.get("encrypted");
        if (!Files.exists(encryptedDirectoryPath)) {
            encryptedDirectoryPath = Files.createDirectory(Paths.get("encrypted"));
        }

        outputFileName = Optional.ofNullable(outputFileName).orElse(Paths.get(filePath).getFileName().toString());
        outputFilePath = Paths.get(encryptedDirectoryPath.toAbsolutePath().toString(), outputFileName);

        Files.deleteIfExists(outputFilePath);
        outputFilePath = Files.createFile(outputFilePath);

        Flow masterSplitFlow = new FlowBuilder<Flow>("masterFlow").start(splitFileTasklet()).build();


        return jobBuilderFactory
                .get("readFileAndEncrypt")
                .incrementer(new RunIdIncrementer())
                .start(masterSplitFlow)
                .next(secondStep())
                .end()
                .build();


    }


}
