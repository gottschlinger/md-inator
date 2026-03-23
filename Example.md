### Shorthand — grabs all Java files from a public repo
java -jar build/libs/md-inator-1.0.0.jar torvalds/linux --include "**/*.c" --include "**/*.h"

### Full URL
java -jar build/libs/md-inator-1.0.0.jar https://github.com/spring-projects/spring-boot --include "**/*.java"

### Specific branch
java -jar build/libs/md-inator-1.0.0.jar spring-projects/spring-boot --include "**/*.java" --branch 3.4.x

### Dry-run first to see what would be matched before burning API requests
java -jar build/libs/md-inator-1.0.0.jar owner/repo --include "**/*.java" --dry-run

### Private repo
md-inator owner/private-repo --include "**/*.java" --token ghp_yourtoken

### Public repo with higher rate limit (5,000/hour instead of 60)
md-inator owner/public-repo --include "**/*.java" --token ghp_yourtoken