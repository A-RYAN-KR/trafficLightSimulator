package org.example.smarttrafficlight.service;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class TelegramBotHandler extends TelegramLongPollingBot {

    private final String botUsername;
    private final String botToken;
    private final String chatId; // The chat ID to send messages to

    // Constructor to receive bot credentials and chat ID
    public TelegramBotHandler(String botUsername, String botToken, String chatId) {
        super(botToken); // Pass token to superclass is the new way
        this.botUsername = botUsername;
        this.botToken = botToken; // Keep a copy if needed elsewhere
        this.chatId = chatId;
    }


    @Override
    public String getBotUsername() {
        return this.botUsername; // Return the bot username
    }

    // No need to override getBotToken() if using the super(token) constructor

    @Override
    public void onUpdateReceived(Update update) {
        // We are primarily using this bot for sending output,
        // but you could handle incoming commands here if needed.
        // Example: Respond to a /status command
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            long receivedChatId = update.getMessage().getChatId();

            System.out.println("Received message: '" + messageText + "' from chat ID: " + receivedChatId);


            if (messageText.equals("/start")) {
                sendMessage(receivedChatId, "Smart Traffic Light Bot started. I will send updates here.");
            } else if (messageText.equals("/help")) {
                sendMessage(receivedChatId, "I send automatic updates about traffic simulation events (e.g., emergencies). No commands needed for that.");
            }
            // Add more command handling here if desired
        }
    }

    // Method to send messages TO the configured chat ID
    public void sendMessage(String messageText) {
        sendMessage(Long.parseLong(this.chatId), messageText); // Send to the pre-configured chat
    }


    // Internal method to send message to any chat ID
    private void sendMessage(long targetChatId, String messageText) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(targetChatId)); // Set target chat
        message.setText(messageText);
        try {
            execute(message); // Send the message
            System.out.println("Sent message to Telegram: " + messageText);
        } catch (TelegramApiException e) {
            System.err.println("Failed to send message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Method to register the bot
    public void registerBot() {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(this);
            System.out.println("Telegram Bot '" + getBotUsername() + "' registered successfully!");
            sendMessage("Bot is online and connected to the simulation.");
        } catch (TelegramApiException e) {
            System.err.println("Error registering Telegram bot: " + e.getMessage());
            e.printStackTrace();
        }
    }
}