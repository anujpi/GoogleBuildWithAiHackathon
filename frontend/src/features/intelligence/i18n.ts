import type { Language } from './types'

// UI labels for the intelligence journey. Data values, crop names and sources stay as the backend sends them.
const en = {
  title: 'Farm intelligence',
  description: 'Land suitability, crop health, supply, demand and risk for one farm, with the evidence behind every value.',
  farm: 'Farm',
  crop: 'Crop',
  language: 'Language',
  region: 'Region',
  conditions: 'Current conditions',
  conditionsDesc: 'Live forecast for the farm location',
  temperature: 'Temperature',
  humidity: 'Humidity',
  rain7: 'Rain, next 7 days',
  maxTemp7: 'Max temperature, next 7 days',
  decision: 'Platform decision',
  decisionDesc: 'Rule-based decision by the backend. Demand never overrides land suitability.',
  alternatives: 'Suitable alternatives',
  suitability: 'Crop suitability',
  suitabilityDesc: 'Weighted evidence index from state-level history, soil pH and water need. Not a probability.',
  supplyDemand: 'Supply, demand and gap',
  supplyDemandDesc: 'State-level. Supply is an ML forecast; demand is a public consumption proxy.',
  supply: 'Supply forecast',
  demand: 'Demand proxy',
  gap: 'Supply − demand',
  outlook: 'Demand outlook',
  risk: 'Risk and anomalies',
  riskDesc: 'Documented rule thresholds and robust anomaly checks on historical series.',
  doctor: 'Crop doctor',
  doctorDesc: 'Upload a potato or tomato leaf photo for an image-based diagnosis.',
  upload: 'Choose leaf photo',
  diagnose: 'Diagnose',
  advisory: 'AI explanation',
  advisoryDesc: 'Google Gemini explains the evidence above in plain language. It cannot add facts.',
  explain: 'Explain with AI',
  readAloud: 'Read aloud',
  stop: 'Stop',
  keyFactors: 'Key factors',
  actions: 'Recommended actions',
  uncertainty: 'Uncertainty and limitations',
  sources: 'Data notices',
  scale: 'Works for any Indian state in the source data (30 states, 1997–2020). Nothing here is hard-coded to one city.',
  noFarms: 'Add a farm to see its intelligence.',
  addFarm: 'Add farm',
}

const hi: typeof en = {
  title: 'खेत की जानकारी',
  description: 'एक खेत के लिए भूमि उपयुक्तता, फसल स्वास्थ्य, आपूर्ति, माँग और जोखिम — हर आँकड़े के प्रमाण के साथ।',
  farm: 'खेत',
  crop: 'फसल',
  language: 'भाषा',
  region: 'क्षेत्र',
  conditions: 'वर्तमान मौसम',
  conditionsDesc: 'खेत के स्थान का लाइव पूर्वानुमान',
  temperature: 'तापमान',
  humidity: 'नमी',
  rain7: 'अगले 7 दिन की वर्षा',
  maxTemp7: 'अगले 7 दिन का अधिकतम तापमान',
  decision: 'प्लेटफ़ॉर्म का निर्णय',
  decisionDesc: 'बैकएंड के नियमों पर आधारित निर्णय। माँग कभी भी भूमि की उपयुक्तता से ऊपर नहीं होती।',
  alternatives: 'उपयुक्त विकल्प',
  suitability: 'फसल उपयुक्तता',
  suitabilityDesc: 'राज्य-स्तरीय इतिहास, मिट्टी pH और पानी की ज़रूरत से बना सूचकांक। यह संभावना नहीं है।',
  supplyDemand: 'आपूर्ति, माँग और अंतर',
  supplyDemandDesc: 'राज्य स्तर। आपूर्ति ML पूर्वानुमान है; माँग सार्वजनिक खपत का अनुमान है।',
  supply: 'आपूर्ति पूर्वानुमान',
  demand: 'माँग अनुमान',
  gap: 'आपूर्ति − माँग',
  outlook: 'माँग का भविष्य अनुमान',
  risk: 'जोखिम और असामान्यताएँ',
  riskDesc: 'दर्ज नियम-सीमाएँ और ऐतिहासिक आँकड़ों पर असामान्यता जाँच।',
  doctor: 'फसल डॉक्टर',
  doctorDesc: 'आलू या टमाटर की पत्ती की फ़ोटो अपलोड करें।',
  upload: 'पत्ती की फ़ोटो चुनें',
  diagnose: 'जाँच करें',
  advisory: 'AI व्याख्या',
  advisoryDesc: 'Google Gemini ऊपर दिए प्रमाणों को सरल भाषा में समझाता है। यह नए तथ्य नहीं जोड़ सकता।',
  explain: 'AI से समझें',
  readAloud: 'सुनें',
  stop: 'रोकें',
  keyFactors: 'मुख्य कारण',
  actions: 'सुझाए गए कदम',
  uncertainty: 'अनिश्चितता और सीमाएँ',
  sources: 'डेटा सूचनाएँ',
  scale: 'स्रोत डेटा के किसी भी भारतीय राज्य (30 राज्य, 1997–2020) के लिए काम करता है। कोई एक शहर तय नहीं है।',
  noFarms: 'जानकारी देखने के लिए एक खेत जोड़ें।',
  addFarm: 'खेत जोड़ें',
}

export const dictionaries: Record<Language, typeof en> = { en, hi }
export type Strings = typeof en

/** Browser speech synthesis. Returns false when the browser has no voice support. */
export function speak(text: string, language: Language, onEnd: () => void): boolean {
  if (typeof window === 'undefined' || !('speechSynthesis' in window)) return false
  window.speechSynthesis.cancel()
  const u = new SpeechSynthesisUtterance(text)
  u.lang = language === 'hi' ? 'hi-IN' : 'en-IN'
  const voice = window.speechSynthesis.getVoices().find((v) => v.lang.toLowerCase().startsWith(language))
  if (voice) u.voice = voice
  u.onend = onEnd
  u.onerror = onEnd
  window.speechSynthesis.speak(u)
  return true
}

export const stopSpeaking = () => {
  if (typeof window !== 'undefined' && 'speechSynthesis' in window) window.speechSynthesis.cancel()
}
