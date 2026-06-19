import { WebPlugin } from '@capacitor/core';
import type { AuthorizationOptions, AuthorizationStatus, AvailabilityResult, HealthPlugin, QueryAggregatedOptions, QueryAggregatedResult, QueryOptions, QueryWorkoutsOptions, QueryWorkoutsResult, ReadSamplesResult, WriteSampleOptions, NutritionSample } from './definitions';
export declare class HealthWeb extends WebPlugin implements HealthPlugin {
    isAvailable(): Promise<AvailabilityResult>;
    requestAuthorization(_options: AuthorizationOptions): Promise<AuthorizationStatus>;
    checkAuthorization(_options: AuthorizationOptions): Promise<AuthorizationStatus>;
    readSamples(_options: QueryOptions): Promise<ReadSamplesResult>;
    saveSample(_options: WriteSampleOptions): Promise<void>;
    saveNutrition(_options: NutritionSample): Promise<void>;
    getPluginVersion(): Promise<{
        version: string;
    }>;
    openHealthConnectSettings(): Promise<void>;
    showPrivacyPolicy(): Promise<void>;
    queryWorkouts(_options: QueryWorkoutsOptions): Promise<QueryWorkoutsResult>;
    queryAggregated(_options: QueryAggregatedOptions): Promise<QueryAggregatedResult>;
}
