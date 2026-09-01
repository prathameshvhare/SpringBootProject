package com.techhub.service;

import com.techhub.entity.*;
import com.techhub.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private final RecommendationRepository recommendationRepository;
    private final CareerRepository careerRepository;
    private final CareerSkillRepository careerSkillRepository;
    private final CareerInterestRepository careerInterestRepository;
    private final UserSkillRepository userSkillRepository;
    private final UserInterestRepository userInterestRepository;
    private final ResultRepository resultRepository;
    private final SkillRepository skillRepository;
    private final InterestRepository interestRepository;
    private final QuestionRepository questionRepository;

    public RecommendationService(RecommendationRepository recommendationRepository,
                                 CareerRepository careerRepository,
                                 CareerSkillRepository careerSkillRepository,
                                 CareerInterestRepository careerInterestRepository,
                                 UserSkillRepository userSkillRepository,
                                 UserInterestRepository userInterestRepository,
                                 ResultRepository resultRepository,
                                 SkillRepository skillRepository,
                                 InterestRepository interestRepository,
                                 QuestionRepository questionRepository) {
        this.recommendationRepository = recommendationRepository;
        this.careerRepository = careerRepository;
        this.careerSkillRepository = careerSkillRepository;
        this.careerInterestRepository = careerInterestRepository;
        this.userSkillRepository = userSkillRepository;
        this.userInterestRepository = userInterestRepository;
        this.resultRepository = resultRepository;
        this.skillRepository = skillRepository;
        this.interestRepository = interestRepository;
        this.questionRepository = questionRepository;
    }

    public Recommendation save(Recommendation recommendation) {
        return recommendationRepository.save(recommendation);
    }

    public Optional<Recommendation> findById(Long id) {
        return recommendationRepository.findById(id);
    }

    public List<Recommendation> findByUserId(Long userId) {
        return recommendationRepository.findByUserId(userId);
    }

    public List<Recommendation> findAll() {
        return recommendationRepository.findAll();
    }

    public void deleteByUserId(Long userId) {
        recommendationRepository.deleteByUserId(userId);
    }

    public void deleteById(Long id) {
        recommendationRepository.deleteById(id);
    }

    @Transactional
    public List<Recommendation> generateForUser(Long userId) {
        recommendationRepository.deleteByUserId(userId);

        List<Career> allCareers = careerRepository.findAll();
        List<UserSkill> userSkills = userSkillRepository.findByUserId(userId);
        List<UserInterest> userInterests = userInterestRepository.findByUserId(userId);
        List<Result> userResults = resultRepository.findByUserId(userId);
        List<Skill> masterSkills = skillRepository.findAll();
        List<Interest> masterInterests = interestRepository.findAll();

        Map<Long, String> skillMap = new HashMap<>();
        for (Skill s : masterSkills) skillMap.put(s.getId(), s.getSkillName().toLowerCase().trim());

        Map<Long, String> interestMap = new HashMap<>();
        for (Interest i : masterInterests) interestMap.put(i.getId(), i.getInterestName().toLowerCase().trim());

        Set<String> primarySkillNames = new HashSet<>();
        Set<String> secondarySkillNames = new HashSet<>();
        for (UserSkill us : userSkills) {
            String name = skillMap.get(us.getSkillId());
            if (name != null) {
                if (Boolean.TRUE.equals(us.getIsPrimary())) {
                    primarySkillNames.add(name);
                } else {
                    secondarySkillNames.add(name);
                }
            }
        }

        Set<String> userInterestNames = new HashSet<>();
        for (UserInterest ui : userInterests) {
            String name = interestMap.get(ui.getInterestId());
            if (name != null) userInterestNames.add(name);
        }

        boolean hasSkillsOnboarded = !primarySkillNames.isEmpty() || !secondarySkillNames.isEmpty();
        double latestAssessmentPercentage = userResults.isEmpty() ? 65.0 : userResults.get(userResults.size() - 1).getPercentage();

        List<Recommendation> generated = new ArrayList<>();

        if (hasSkillsOnboarded) {
            // =========================================================================
            // CASE A: DYNAMIC TECHNICAL SKILL ASSESSMENT (NATURAL SCORE MODEL)
            // Math: Skill Alignment (50%) + Interest Match (20%) + Test Score (30%)
            // =========================================================================
            for (Career career : allCareers) {
                String cNameLower = career.getCareerName().toLowerCase();
                String cDescLower = (career.getDescription() != null ? career.getDescription() : "").toLowerCase();
                String reqSkillsStr = career.getRequiredSkills() != null ? career.getRequiredSkills() : "";
                List<String> requiredSkillsList = Arrays.stream(reqSkillsStr.split(","))
                        .map(s -> s.trim().toLowerCase())
                        .filter(s -> !s.isEmpty())
                        .toList();

                double skillPoints = 0.0;
                if (!requiredSkillsList.isEmpty()) {
                    for (String reqSkill : requiredSkillsList) {
                        boolean isPrimary = primarySkillNames.stream().anyMatch(ps -> ps.contains(reqSkill) || reqSkill.contains(ps));
                        boolean isSecondary = secondarySkillNames.stream().anyMatch(ss -> ss.contains(reqSkill) || reqSkill.contains(ss));

                        if (isPrimary) {
                            skillPoints += 1.0;
                        } else if (isSecondary) {
                            skillPoints += 0.6;
                        }
                    }
                    double skillRatio = skillPoints / (double) requiredSkillsList.size();
                    skillPoints = Math.min(50.0, skillRatio * 50.0);
                } else {
                    skillPoints = 20.0;
                }

                boolean interestMatch = userInterestNames.stream().anyMatch(in -> cNameLower.contains(in) || cDescLower.contains(in));
                double interestScore = interestMatch ? 20.0 : 5.0;
                double testScore = (latestAssessmentPercentage / 100.0) * 30.0;

                double totalMatchScore = skillPoints + interestScore + testScore;
                totalMatchScore = Math.min(98.5, Math.max(35.0, totalMatchScore));
                totalMatchScore = Math.round(totalMatchScore * 10.0) / 10.0;

                Recommendation rec = new Recommendation();
                rec.setUserId(userId);
                rec.setCareerId(career.getId());
                rec.setMatchScore(totalMatchScore);
                generated.add(recommendationRepository.save(rec));
            }
        } else {
            // =========================================================================
            // CASE B: CAREER DISCOVERY ASSESSMENT (NATURAL DOMAIN PROFILING)
            // Math: Performance-Scaled Base Domain Match + Interest Boost
            // =========================================================================
            double perfFactor = 0.55 + (latestAssessmentPercentage / 200.0);

            for (Career career : allCareers) {
                String cNameLower = career.getCareerName().toLowerCase();
                String cDescLower = (career.getDescription() != null ? career.getDescription() : "").toLowerCase();

                // Dynamic Domain Baseline based on cognitive & technical complexity
                double baseDomainScore = 65.0;
                if (cNameLower.contains("data scientist") || cNameLower.contains("ai") || cNameLower.contains("machine learning")) {
                    baseDomainScore = 88.0;
                } else if (cNameLower.contains("java") || cNameLower.contains("full stack") || cNameLower.contains("software engineer")) {
                    baseDomainScore = 84.0;
                } else if (cNameLower.contains("cloud") || cNameLower.contains("devops")) {
                    baseDomainScore = 79.0;
                } else if (cNameLower.contains("cyber") || cNameLower.contains("security")) {
                    baseDomainScore = 77.0;
                } else if (cNameLower.contains("backend") || cNameLower.contains("database") || cNameLower.contains("dba")) {
                    baseDomainScore = 76.0;
                } else if (cNameLower.contains("mobile") || cNameLower.contains("android") || cNameLower.contains("ios")) {
                    baseDomainScore = 73.0;
                } else if (cNameLower.contains("frontend") || cNameLower.contains("ui") || cNameLower.contains("web")) {
                    baseDomainScore = 70.0;
                } else if (cNameLower.contains("qa") || cNameLower.contains("testing") || cNameLower.contains("quality")) {
                    baseDomainScore = 67.0;
                } else if (cNameLower.contains("business") || cNameLower.contains("analyst") || cNameLower.contains("financial")) {
                    baseDomainScore = 62.0;
                } else if (cNameLower.contains("marketing") || cNameLower.contains("hr") || cNameLower.contains("manager")) {
                    baseDomainScore = 55.0;
                }

                // Component 1: Performance-Scaled Base Match
                double scaledBase = baseDomainScore * perfFactor;

                // Component 2: Interest Fit Boost
                double interestBoost = 0.0;
                if (!userInterestNames.isEmpty()) {
                    boolean directMatch = userInterestNames.stream().anyMatch(in -> 
                        cNameLower.contains(in) || in.contains(cNameLower) || cDescLower.contains(in)
                    );
                    if (directMatch) {
                        interestBoost = 10.0;
                    } else {
                        boolean partialMatch = userInterestNames.stream().anyMatch(in -> {
                            String[] words = in.split("\s+");
                            for (String w : words) {
                                if (w.length() > 3 && (cNameLower.contains(w) || cDescLower.contains(w))) return true;
                            }
                            return false;
                        });
                        interestBoost = partialMatch ? 5.0 : 0.0;
                    }
                }

                double totalMatchScore = scaledBase + interestBoost;
                totalMatchScore = Math.min(98.5, Math.max(35.0, totalMatchScore));
                totalMatchScore = Math.round(totalMatchScore * 10.0) / 10.0;

                Recommendation rec = new Recommendation();
                rec.setUserId(userId);
                rec.setCareerId(career.getId());
                rec.setMatchScore(totalMatchScore);
                generated.add(recommendationRepository.save(rec));
            }
        }

        generated.sort((r1, r2) -> Double.compare(r2.getMatchScore(), r1.getMatchScore()));
        return generated;
    }
}
