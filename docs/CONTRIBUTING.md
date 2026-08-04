# Contributing

Thanks for helping improve WifiAR.

## Workflow

1. Fork the repository (or create a branch if you have write access).  
2. Create a focused branch: `feature/…` or `fix/…`.  
3. Make changes with clear commits.  
4. Open a pull request describing:
   - What changed  
   - Why  
   - Devices / OS versions tested  

## Guidelines

- Prefer small PRs over large mixed changes.  
- Keep scanner logic independent from AR rendering when possible.  
- Do not commit secrets (`local.properties` keys, JWT secrets, production passwords).  
- Match existing Kotlin style (Compose, Material 3, coroutines).  
- Note any intentional behavior change in the PR body.

## Testing checklist

- [ ] App builds (`./gradlew :app:assembleDebug`)  
- [ ] Cold start + onboarding path  
- [ ] Start / end session without crash  
- [ ] Heatmap appears after enough samples  
- [ ] History empty + populated states  
- [ ] Export share sheet opens  
- [ ] (If applicable) backend register/login/sync  

## Code map

See [ARCHITECTURE.md](ARCHITECTURE.md) for package responsibilities.

## Questions

Open a GitHub issue for bugs, ideas, or device-specific ARCore quirks.
