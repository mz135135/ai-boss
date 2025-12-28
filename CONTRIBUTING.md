# Contributing to AI Boss

First off, thank you for considering contributing to AI Boss! It's people like you that make AI Boss such a great tool.

## Code of Conduct

This project and everyone participating in it is governed by respect and professionalism. Please be kind and courteous to others.

## How Can I Contribute?

### Reporting Bugs

Before creating bug reports, please check existing issues to avoid duplicates. When you create a bug report, please include as many details as possible:

- **Use a clear and descriptive title**
- **Describe the exact steps to reproduce the problem**
- **Provide specific examples**
- **Describe the behavior you observed and what you expected**
- **Include screenshots or GIFs if possible**
- **Include your environment details** (Android version, device model, app version)

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, please include:

- **Use a clear and descriptive title**
- **Provide a detailed description of the suggested enhancement**
- **Explain why this enhancement would be useful**
- **List some examples of how it would work**

### Pull Requests

1. Fork the repo and create your branch from `main`
2. If you've added code that should be tested, add tests
3. Ensure the test suite passes
4. Make sure your code follows the existing code style
5. Write a clear commit message
6. Submit a pull request!

## Development Setup

1. Clone the repository:
   ```bash
   git clone https://github.com/mz135135/ai-boss.git
   cd ai-boss
   ```

2. Configure API keys:
   ```bash
   cp api.properties.example api.properties
   # Edit api.properties with your credentials
   ```

3. Build the project:
   ```bash
   ./gradlew assembleDebug
   ```

4. Run tests:
   ```bash
   ./gradlew test
   ./gradlew connectedAndroidTest
   ```

## Coding Guidelines

### Kotlin Style

- Follow the [official Kotlin style guide](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add comments for complex logic
- Keep functions small and focused

### Git Commit Messages

- Use the present tense ("Add feature" not "Added feature")
- Use the imperative mood ("Move cursor to..." not "Moves cursor to...")
- Limit the first line to 72 characters or less
- Reference issues and pull requests after the first line

Examples:
```
Add voice command timeout setting

- Implement configurable timeout for voice recognition
- Add UI setting in user preferences
- Update documentation

Fixes #123
```

### Testing

- Write unit tests for new features
- Ensure all tests pass before submitting PR
- Aim for meaningful test coverage

### Documentation

- Update README.md if you change functionality
- Add inline comments for complex code
- Update the Chinese documentation (项目使用文档.md) as well

## Project Structure

- `app/src/main/java/com/aiautomation/ai/` - AI client implementation
- `app/src/main/java/com/aiautomation/automation/` - Automation engine
- `app/src/main/java/com/aiautomation/service/` - Android services
- `app/src/main/java/com/aiautomation/ui/` - UI components
- `app/src/main/java/com/aiautomation/voice/` - Voice recognition
- `app/src/test/` - Unit tests
- `app/src/androidTest/` - Instrumented tests

## Questions?

Feel free to open an issue with the "question" label if you have any questions about contributing!

## License

By contributing, you agree that your contributions will be licensed under the MIT License.
