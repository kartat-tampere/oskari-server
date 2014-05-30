package fi.nls.oskari.fe.engine;

import java.io.IOException;

import javax.xml.stream.XMLStreamException;

import fi.nls.oskari.fe.input.InputProcessor;
import fi.nls.oskari.fe.input.format.gml.recipe.GroovyParserRecipe;
import fi.nls.oskari.fe.output.OutputProcessor;

public class GroovyFeatureEngine implements FeatureEngine {

	private InputProcessor inputProcessor;
	private OutputProcessor outputProcessor;

	GroovyParserRecipe recipe;

	public void process() throws IOException, XMLStreamException {

		try {
			outputProcessor.begin();

			try {

				recipe.setInputOutput(inputProcessor, outputProcessor);
				recipe.parse();

			} finally {
				inputProcessor.close();
			}

			outputProcessor.flush();
		} finally {
			outputProcessor.end();
		}

	}

	public GroovyParserRecipe getRecipe() {
		return recipe;
	}

	public void setRecipe(GroovyParserRecipe recipe) {
		this.recipe = recipe;
	}

	public void setInputProcessor(InputProcessor inputProcessor) {
		this.inputProcessor = inputProcessor;
	}

	public void setOutputProcessor(OutputProcessor outputProcessor) {
		this.outputProcessor = outputProcessor;
	}

}