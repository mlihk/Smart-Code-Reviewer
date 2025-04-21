package com.fdmgroup.smart_code_assistant.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import com.fdmgroup.smart_code_assistant.model.ChatMessage;
import com.fdmgroup.smart_code_assistant.model.ChatSession;
import com.fdmgroup.smart_code_assistant.repo.ChatMessageRepository;
import com.fdmgroup.smart_code_assistant.repo.ChatSessionRepository;

@Service
public class ChatMessageService {

	private static final Logger logger = LoggerFactory.getLogger(ChatMessageService.class);

	@Autowired
	private ChatMessageRepository chatRepository;
	@Autowired
	private ChatSessionRepository chatSessionRepository;

	@Autowired
	@Qualifier("qwenChatClient")
	private ChatClient qwenChatClient;

	@Autowired
	@Qualifier("deepseekChatClient")
	private ChatClient deepseekChatClient;

	public ChatMessage createResponse(Long sessionId, ChatMessage userInput) throws Exception {
		logger.info("Creating response for session {} with model {}", sessionId, userInput.getModel());

		Optional<ChatSession> result = chatSessionRepository.findById(sessionId);
		if (result.isEmpty()) {
			logger.error("Session not found: {}", sessionId);
			throw new Exception("Session not found");
		}

		ChatSession currentSession = result.get();
		String botType = currentSession.getBotType();
		logger.info("Using bot type: {} for session {}", botType, sessionId);

		try {
			userInput.setRole("user");
			userInput.setCreatedDateTime(LocalDateTime.now());

			// Case-insensitive model selection
			String model = userInput.getModel() != null ? userInput.getModel().toLowerCase() : "qwen";
			ChatClient selectedClient = "deepseek".equals(model) ? deepseekChatClient : qwenChatClient;

			logger.info("Using model: {} for session {}", model, sessionId);

			// Create a system message based on the bot type
			String systemPrompt = getSystemPromptForBotType(botType);
			Message systemMessage = new SystemMessage(systemPrompt);
			Message userMessage = new UserMessage(userInput.getMessage());

			// Create a prompt with both system and user messages
			Prompt prompt = new Prompt(List.of(systemMessage, userMessage));
			String response = selectedClient.call(prompt).getResult().getOutput().getContent();

			if (response == null || response.trim().isEmpty()) {
				logger.error("Empty response received from AI model for session {}", sessionId);
				throw new Exception("Empty response from AI model");
			}

			ChatMessage aiResponse = new ChatMessage();
			aiResponse.setRole("assistant");
			aiResponse.setMessage(response);
			aiResponse.setCreatedDateTime(LocalDateTime.now());
			aiResponse.setModel(model);

			// Save messages and update session
			chatRepository.save(userInput);
			chatRepository.save(aiResponse);

			currentSession.addMessage(userInput);
			currentSession.addMessage(aiResponse);
			chatSessionRepository.save(currentSession);

			logger.info("Successfully created response for session {}", sessionId);
			return aiResponse;

		} catch (Exception e) {
			logger.error("Error creating response for session {}: {}", sessionId, e.getMessage(), e);
			throw e;
		}
	}

	private String getSystemPromptForBotType(String botType) {
		String baseInstructions = "";

		switch (botType) {
			case "code_review":
				return baseInstructions + " You are a code review bot named Code Review Bot. "
						+ "You are a chatbot engineered by the Code Reapers Team. "
						+ "Your role is to analyze code, identify potential issues, "
						+ "and suggest improvements. Focus on code quality, readability, "
						+ "and adherence to best practices for the specified language or framework. ";
			case "code_generator":
				return baseInstructions + " You are a code generator bot named Code Generator Bot. "
						+ "You are a chatbot engineered by the Code Reapers Team. "
						+ "Your role is to generate clean, efficient, and well-documented code "
						+ "based on user requirements. Focus on writing concise and maintainable code, "
						+ "following best practices and conventions for the specified language. ";
			case "code_explainer":
				return baseInstructions + " You are a code explainer bot named Code Explainer Bot. "
						+ "You are a chatbot engineered by the Code Reapers Team. "
						+ "Your role is to help users understand code by providing clear and detailed explanations. "
						+ "Focus on breaking down complex concepts into simpler terms, "
						+ "explaining algorithms step by step, and providing examples to illustrate key points. ";
			case "leetcode":
				return baseInstructions + " You are a LeetCode problem-solving bot named LeetCode Bot. "
						+ "You are a chatbot engineered by the Code Reapers Team. "
						+ "Your role is to help users solve coding problems by providing step-by-step explanations, "
						+ "optimal solutions, and alternative approaches. Focus on analyzing problems, "
						+ "providing solutions with clear logic, and explaining time and space complexity. "
						+ "Suggest alternative approaches and compare their efficiency. ";
			default:
				return baseInstructions + " You are a general-purpose coding assistant named General Code Reaper. "
						+ "You are a chatbot engineered by the Code Reapers Team. "
						+ "Your role is to assist users with coding questions by providing clear explanations, "
						+ "practical solutions, and helpful insights. Focus on answering questions across multiple "
						+ "languages and frameworks, providing examples, best practices, and debugging steps. "
						+ "Tailor responses to the user's expertise and reference relevant resources if applicable. "
						+ "When asked, identify yourself as a chatbot created by the Code Reapers Team.";
		}
	}

	private ChatMessage createModelChangeMessage(String newModel, ChatSession session) {
		ChatMessage systemMessage = new ChatMessage();
		systemMessage.setRole("system");
		systemMessage.setMessage("AI Model changed to " + (newModel.equals("qwen") ? "Qwen 2.5 Coder" : "DeepSeek R1"));
		systemMessage.setCreatedDateTime(LocalDateTime.now());
		systemMessage.setModel(newModel);
		systemMessage.setChatSession(session);
		return systemMessage;
	}

}
