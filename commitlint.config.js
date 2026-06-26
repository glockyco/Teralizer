module.exports = {
	rules: {
		"type-enum": [
			2,
			"always",
			[
				"feat",
				"fix",
				"docs",
				"style",
				"refactor",
				"perf",
				"test",
				"build",
				"ci",
				"chore",
				"revert",
			],
		],
		"type-empty": [2, "never"],
		"subject-empty": [2, "never"],
		"header-max-length": [2, "always", 72],
		"scope-case": [2, "always", "lower-case"],
		"subject-full-stop": [2, "never", "."],
		"body-max-line-length": [2, "always", 72],
	},
};
